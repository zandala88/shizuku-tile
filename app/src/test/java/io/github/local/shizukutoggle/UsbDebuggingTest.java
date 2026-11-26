package io.github.local.shizukutoggle;

import android.Manifest;
import android.app.Application;
import android.provider.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UsbDebuggingTest {
    @Test public void grantedWriterDisablesUsbButLeavesWirelessDebuggingUntouched() {
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        Settings.Global.putInt(app.getContentResolver(), Settings.Global.ADB_ENABLED, 1);
        Settings.Global.putInt(app.getContentResolver(), "adb_wifi_enabled", 1);
        UsbDebugging usb = new UsbDebugging(app);
        assertTrue(usb.canWrite());
        assertEquals(UsbDebugging.Result.DISABLED, usb.disable());
        assertEquals(0, Settings.Global.getInt(app.getContentResolver(), Settings.Global.ADB_ENABLED, -1));
        assertEquals(1, Settings.Global.getInt(app.getContentResolver(), "adb_wifi_enabled", -1));
    }

    @Test public void missingPermissionNeverChangesUsbDebugging() {
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        Settings.Global.putInt(app.getContentResolver(), Settings.Global.ADB_ENABLED, 1);
        UsbDebugging usb = new UsbDebugging(app);
        assertFalse(usb.canWrite());
        assertEquals(UsbDebugging.Result.PERMISSION_DENIED, usb.disable());
        assertEquals(1, Settings.Global.getInt(app.getContentResolver(), Settings.Global.ADB_ENABLED, -1));
    }
}
