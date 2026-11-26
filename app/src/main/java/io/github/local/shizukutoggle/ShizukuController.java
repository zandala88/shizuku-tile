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
    private final UsbDebugging usb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Runnable> observers = new LinkedHashSet<>();
    private final Runnable poll = this::refresh;
    private boolean probeFinished;
    private boolean managerWasMissing;
    private boolean pendingUsbCleanup;

    ShizukuController(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        usb = new UsbDebugging(this.context);
        bridge = new ShizukuBridge(this.context);
        bridge.setOnDeath(() -> receive(ToggleState.Actual.STOPPED));
        String saved = prefs.getString("pending", null);
        if (saved != null) {
            try {
                state.restore(ToggleState.Command.valueOf(saved), prefs.getLong("deadline", 0), now());
            } catch (IllegalArgumentException ignored) { prefs.edit().remove("pending").remove("deadline").apply(); }
        }
        pendingUsbCleanup = state.pending() == ToggleState.Command.STOP && closeUsbOnStop()
                && prefs.getBoolean("pending_usb_cleanup", false);
        persistPending();
        if (pendingUsbCleanup) main.post(poll);
    }

    void addObserver(Runnable observer) {
        boolean first = observers.isEmpty();
        observers.add(observer);
        if (first && !pendingUsbCleanup) {
            probeFinished = false;
            state.observe(ToggleState.Actual.UNKNOWN, now());
            refresh();
        }
        observer.run();
    }

    void removeObserver(Runnable observer) {
        observers.remove(observer);
        stopIfIdle();
    }

    private boolean needsMonitoring() { return !observers.isEmpty() || pendingUsbCleanup; }

    private void stopIfIdle() {
        if (needsMonitoring()) return;
        main.removeCallbacks(poll);
        bridge.close();
    }

    private void refresh() {
        if (!needsMonitoring()) return;
        main.removeCallbacks(poll);
        if (!bridge.isInstalled()) {
            managerWasMissing = true;
            state.observe(ToggleState.Actual.UNKNOWN, now());
            state.fail("未安装 Shizuku");
            pendingUsbCleanup = false;
            probeFinished = true;
            persistPending();
            notifyObservers();
            if (needsMonitoring()) main.postDelayed(poll, 2_000);
            else stopIfIdle();
            return;
        }
        if (managerWasMissing) {
            state.clearNotice();
            managerWasMissing = false;
        }
        bridge.probe(this::receive);
    }

    private void receive(ToggleState.Actual actual) {
        if (!needsMonitoring()) return;
        probeFinished = true;
        ToggleState.Command before = state.pending();
        state.observe(actual, now());
        if (pendingUsbCleanup && state.pending() == null) {
            pendingUsbCleanup = false;
            // Clear the request before changing a system setting; never replay it on unrelated stops.
            boolean consumed = persistPending(true);
            if (!consumed) {
                state.fail("USB 调试未关闭：无法保存操作状态");
            } else if (before == ToggleState.Command.STOP && actual == ToggleState.Actual.STOPPED && !state.hasNotice()) {
                UsbDebugging.Result result = usb.disable();
                if (result == UsbDebugging.Result.PERMISSION_DENIED)
                    state.fail("Shizuku 已停止，USB 调试未关闭：请先完成 ADB 授权");
                else if (result == UsbDebugging.Result.FAILED)
                    state.fail("Shizuku 已停止，但系统拒绝关闭 USB 调试");
            }
        }
        persistPending();
        notifyObservers();
        main.removeCallbacks(poll);
        if (needsMonitoring()) main.postDelayed(poll, state.pending() != null ? 250
                : actual == ToggleState.Actual.UNKNOWN ? 200 : 750);
        else stopIfIdle();
    }

    void toggle() {
        ToggleState.Command command = state.begin(!token().isEmpty(), now());
        if (command == null) { notifyObservers(); return; }
        pendingUsbCleanup = command == ToggleState.Command.STOP && closeUsbOnStop();
        persistPending();
        // Publish progress before any Binder work involved in sending the command.
        notifyObservers();
        try {
            context.sendBroadcast(BroadcastProtocol.command(command, token()));
        } catch (RuntimeException e) {
            state.fail("广播发送失败，请打开 Shizuku 检查");
            pendingUsbCleanup = false;
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
    boolean closeUsbOnStop() { return prefs.getBoolean("close_usb_on_stop", false); }
    boolean setCloseUsbOnStop(boolean enabled) {
        if (enabled && !usb.canWrite()) return false;
        prefs.edit().putBoolean("close_usb_on_stop", enabled).apply();
        if (!enabled && pendingUsbCleanup) {
            pendingUsbCleanup = false;
            persistPending();
            stopIfIdle();
        }
        notifyObservers();
        return true;
    }
    boolean canControlUsb() { return usb.canWrite(); }
    ViewState view() { return new ViewState(state, !token().isEmpty(), probeFinished); }

    private void persistPending() {
        persistPending(false);
    }

    private boolean persistPending(boolean durable) {
        String pending = state.pending() == null ? null : state.pending().name();
        if (!durable && java.util.Objects.equals(pending, prefs.getString("pending", null))
                && state.deadline() == prefs.getLong("deadline", 0)
                && pendingUsbCleanup == prefs.getBoolean("pending_usb_cleanup", false)) return true;
        SharedPreferences.Editor editor = prefs.edit();
        if (pending == null) editor.remove("pending").remove("deadline");
        else editor.putString("pending", pending).putLong("deadline", state.deadline());
        if (pendingUsbCleanup) editor.putBoolean("pending_usb_cleanup", true);
        else editor.remove("pending_usb_cleanup");
        // A tiny synchronous commit is needed only when consuming a privileged cleanup request.
        if (durable) return editor.commit();
        editor.apply();
        return true;
    }

    private void notifyObservers() {
        for (Runnable observer : new ArrayList<>(observers)) observer.run();
    }
    private static long now() { return System.currentTimeMillis(); }
}
