package io.github.local.shizukutoggle;

import android.Manifest;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.Switch;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UsbSettingsTest {
    @Before public void isolateApplicationSingleton() {
        org.robolectric.util.ReflectionHelpers.setStaticField(ShizukuController.class, "instance", null);
    }
    @Test public void switchDefaultsOffAndPersistsAfterRecreationWhenAuthorized() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        try (ActivityController<MainActivity> activity = Robolectric.buildActivity(MainActivity.class).setup()) {
            Switch option = activity.get().findViewById(R.id.close_usb_on_stop);
            assertNotNull("A USB debugging stop option must be available", option);
            assertFalse(option.isChecked());
            option.performClick();
            assertTrue(ShizukuController.get(activity.get()).closeUsbOnStop());
            activity.recreate();
            assertTrue(((Switch) activity.get().findViewById(R.id.close_usb_on_stop)).isChecked());
            assertEquals(View.GONE, activity.get().findViewById(R.id.usb_permission_help).getVisibility());
        }
    }

    @Test public void withoutPermissionSwitchStaysOffAndGrantCommandCanBeCopied() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        try (ActivityController<MainActivity> activity = Robolectric.buildActivity(MainActivity.class).setup()) {
            Switch option = activity.get().findViewById(R.id.close_usb_on_stop);
            assertNotNull(option);
            option.performClick();
            assertFalse(option.isChecked());
            assertFalse(ShizukuController.get(activity.get()).closeUsbOnStop());
            assertEquals(View.VISIBLE, activity.get().findViewById(R.id.usb_permission_help).getVisibility());
            activity.get().findViewById(R.id.copy_usb_grant).performClick();
            ClipboardManager clipboard = (ClipboardManager) activity.get().getSystemService(Context.CLIPBOARD_SERVICE);
            assertEquals("adb shell pm grant io.github.local.shizukutoggle android.permission.WRITE_SECURE_SETTINGS",
                    clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        }
    }
}
