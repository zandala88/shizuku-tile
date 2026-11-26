package io.github.local.shizukutoggle;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.function.Consumer;

/** Reads the callback protocol implemented by Shizuku's ShellBinderRequestHandler. */
final class ShizukuBridge {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    private IBinder callbackBinder;
    private IBinder serverBinder;
    private IBinder.DeathRecipient deathRecipient;
    private Runnable timeout;
    private Runnable onDeath = () -> { };
    private boolean nullReplySeen;

    ShizukuBridge(Context context) { this.context = context.getApplicationContext(); }

    boolean isInstalled() {
        try {
            context.getPackageManager().getPackageInfo(BroadcastProtocol.PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    void setOnDeath(Runnable listener) { onDeath = listener; }

    void probe(Consumer<ToggleState.Actual> callback) {
        cancelRequest();
        final int request = generation;
        // Keep a strong reference until reply/timeout: the Binder is sent to another process.
        callbackBinder = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code != 1) return super.onTransact(code, data, reply, flags);
                IBinder remote = data.readStrongBinder();
                main.post(() -> {
                    if (request != generation) return;
                    finish(request, callback, readStatus(remote));
                });
                return true;
            }
        };
        timeout = () -> finish(request, callback, ToggleState.Actual.UNKNOWN);
        main.postDelayed(timeout, 2_000);
        try {
            context.sendBroadcast(BroadcastProtocol.status(callbackBinder));
        } catch (RuntimeException e) {
            finish(request, callback, ToggleState.Actual.UNKNOWN);
        }
    }

    private void finish(int request, Consumer<ToggleState.Actual> callback, ToggleState.Actual value) {
        if (request != generation) return;
        cancelRequest();
        callback.accept(value);
    }

    private void cancelRequest() {
        generation++;
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = null;
        callbackBinder = null;
    }

    private ToggleState.Actual readStatus(IBinder remote) {
        // A cold manager can reply before it has received the server Binder.
        // Prefer direct evidence from a known Binder and confirm an initial null on a later probe.
        IBinder candidate = remote != null ? remote : serverBinder;
        if (candidate != null) {
            if (candidate.pingBinder() && watch(candidate)) {
                nullReplySeen = false;
                return ToggleState.Actual.RUNNING;
            }
            unwatch();
            nullReplySeen = true;
            return ToggleState.Actual.STOPPED;
        }
        boolean confirmed = nullReplySeen;
        nullReplySeen = true;
        return confirmed ? ToggleState.Actual.STOPPED : ToggleState.Actual.UNKNOWN;
    }

    private boolean watch(IBinder remote) {
        if (serverBinder == remote) return true;
        unwatch();
        serverBinder = remote;
        deathRecipient = () -> main.post(() -> {
            if (serverBinder != remote) return;
            unwatch();
            onDeath.run();
        });
        try {
            remote.linkToDeath(deathRecipient, 0);
            return true;
        } catch (RemoteException e) {
            // Registration failed, so there is no death recipient to unlink.
            serverBinder = null;
            deathRecipient = null;
            return false;
        }
    }

    private void unwatch() {
        if (serverBinder != null && deathRecipient != null) {
            try { serverBinder.unlinkToDeath(deathRecipient, 0); }
            catch (java.util.NoSuchElementException ignored) { /* Already removed by Binder death. */ }
        }
        serverBinder = null;
        deathRecipient = null;
    }

    void close() {
        cancelRequest();
        unwatch();
        nullReplySeen = false;
    }
}
