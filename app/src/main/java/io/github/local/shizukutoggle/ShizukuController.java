package io.github.local.shizukutoggle;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

final class ShizukuController {
    private static ShizukuController instance;
    static ShizukuController get(Context context) {
        if (instance == null) instance = new ShizukuController(context);
        return instance;
    }

    static final class ViewState {
        final ToggleState.Actual actual;
        final ToggleState.Command pending;
        final boolean configured;
        final boolean canToggle;
        final boolean needsAttention;
        final String message;
        ViewState(ToggleState state, boolean configured, boolean probeFinished) {
            actual = state.actual();
            pending = state.pending();
            this.configured = configured;
            canToggle = configured && actual != ToggleState.Actual.UNKNOWN && pending == null;
            needsAttention = !configured || state.hasNotice()
                    || (actual == ToggleState.Actual.UNKNOWN && pending == null && probeFinished);
            String text = state.message();
            if (!configured) text = "请先打开应用填写 auth";
            else if (actual == ToggleState.Actual.UNKNOWN && pending == null && probeFinished
                    && text.equals("正在检测…")) text = "无法读取状态，请打开 Shizuku 检查";
            message = text;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final ToggleState state = new ToggleState();
    private final ShizukuBridge bridge;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Runnable> observers = new LinkedHashSet<>();
    private final Runnable poll = this::refresh;
    private boolean probeFinished;
    private boolean managerWasMissing;

    ShizukuController(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        bridge = new ShizukuBridge(this.context);
        bridge.setOnDeath(() -> receive(ToggleState.Actual.STOPPED));
        String saved = prefs.getString("pending", null);
        if (saved != null) {
            try {
                state.restore(ToggleState.Command.valueOf(saved), prefs.getLong("deadline", 0), now());
            } catch (IllegalArgumentException ignored) { prefs.edit().remove("pending").remove("deadline").apply(); }
        }
    }

    void addObserver(Runnable observer) {
        boolean first = observers.isEmpty();
        observers.add(observer);
        if (first) {
            probeFinished = false;
            state.observe(ToggleState.Actual.UNKNOWN, now());
            refresh();
        }
        observer.run();
    }

    void removeObserver(Runnable observer) {
        observers.remove(observer);
        if (observers.isEmpty()) {
            main.removeCallbacks(poll);
            bridge.close();
        }
    }

    private void refresh() {
        if (observers.isEmpty()) return;
        main.removeCallbacks(poll);
        if (!bridge.isInstalled()) {
            managerWasMissing = true;
            state.observe(ToggleState.Actual.UNKNOWN, now());
            state.fail("未安装 Shizuku");
            probeFinished = true;
            persistPending();
            notifyObservers();
            main.postDelayed(poll, 2_000);
            return;
        }
        if (managerWasMissing) {
            state.clearNotice();
            managerWasMissing = false;
        }
        bridge.probe(this::receive);
    }

    private void receive(ToggleState.Actual actual) {
        if (observers.isEmpty()) return;
        probeFinished = true;
        state.observe(actual, now());
        persistPending();
        notifyObservers();
        main.removeCallbacks(poll);
        main.postDelayed(poll, state.pending() != null ? 250
                : actual == ToggleState.Actual.UNKNOWN ? 200 : 750);
    }

    void toggle() {
        ToggleState.Command command = state.begin(!token().isEmpty(), now());
        if (command == null) { notifyObservers(); return; }
        persistPending();
        // Publish progress before any Binder work involved in sending the command.
        notifyObservers();
        try {
            context.sendBroadcast(BroadcastProtocol.command(command, token()));
        } catch (RuntimeException e) {
            state.fail("广播发送失败，请打开 Shizuku 检查");
            persistPending();
        }
        if (state.pending() == null) notifyObservers();
        refresh();
    }

    boolean saveToken(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return false;
        prefs.edit().putString("auth", clean).apply();
        notifyObservers();
        return true;
    }

    String token() { return prefs.getString("auth", BuildConfig.INITIAL_AUTH_TOKEN).trim(); }
    ViewState view() { return new ViewState(state, !token().isEmpty(), probeFinished); }

    private void persistPending() {
        String pending = state.pending() == null ? null : state.pending().name();
        if (java.util.Objects.equals(pending, prefs.getString("pending", null))
                && state.deadline() == prefs.getLong("deadline", 0)) return;
        SharedPreferences.Editor editor = prefs.edit();
        if (pending == null) editor.remove("pending").remove("deadline");
        else editor.putString("pending", pending).putLong("deadline", state.deadline());
        editor.apply();
    }

    private void notifyObservers() {
        for (Runnable observer : new ArrayList<>(observers)) observer.run();
    }
    private static long now() { return System.currentTimeMillis(); }
}
