package io.github.local.shizukutoggle;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

final class BroadcastProtocol {
    static final String PACKAGE = "moe.shizuku.privileged.api";
    static Intent command(ToggleState.Command command, String token) {
        return scoped(PACKAGE + "." + command.name()).putExtra("auth", token);
    }
    static Intent status(IBinder callback) {
        Bundle data = new Bundle();
        data.putBinder("binder", callback);
        return scoped("rikka.shizuku.intent.action.REQUEST_BINDER").putExtra("data", data);
    }
    private static Intent scoped(String action) {
        return new Intent(action).setPackage(PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
    }
}
