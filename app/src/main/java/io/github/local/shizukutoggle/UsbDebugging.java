package io.github.local.shizukutoggle;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

final class UsbDebugging {
    static final String GRANT_COMMAND = "adb shell pm grant " + BuildConfig.APPLICATION_ID
            + " android.permission.WRITE_SECURE_SETTINGS";
    enum Result { DISABLED, PERMISSION_DENIED, FAILED }
    private final Context context;
    UsbDebugging(Context context) { this.context = context.getApplicationContext(); }
    boolean canWrite() {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }
    Result disable() {
        if (!canWrite()) return Result.PERMISSION_DENIED;
        try {
            // Do not read ADB_ENABLED to skip this write: Android masks its value for third-party apps.
            return Settings.Global.putInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0)
                    ? Result.DISABLED : Result.FAILED;
        } catch (RuntimeException e) { return Result.FAILED; }
    }
}
