package io.github.local.shizukutoggle;

import android.content.Intent;
import android.os.Binder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BroadcastProtocolTest {
    @Test public void startIsPackageScopedAndCarriesAuth() {
        Intent intent = BroadcastProtocol.command(ToggleState.Command.START, "test-token");
        assertEquals("moe.shizuku.privileged.api.START", intent.getAction());
        assertEquals("moe.shizuku.privileged.api", intent.getPackage());
        assertEquals("test-token", intent.getStringExtra("auth"));
        assertTrue((intent.getFlags() & Intent.FLAG_INCLUDE_STOPPED_PACKAGES) != 0);
    }

    @Test public void stopAlsoCarriesAuthAndUsesTheSamePackage() {
        Intent intent = BroadcastProtocol.command(ToggleState.Command.STOP, "test-token");
        assertEquals("moe.shizuku.privileged.api.STOP", intent.getAction());
        assertEquals("moe.shizuku.privileged.api", intent.getPackage());
        assertEquals("test-token", intent.getStringExtra("auth"));
    }

    @Test public void statusRequestContainsCallbackButNeverAuth() {
        Binder callback = new Binder();
        Intent intent = BroadcastProtocol.status(callback);
        assertEquals("rikka.shizuku.intent.action.REQUEST_BINDER", intent.getAction());
        assertEquals("moe.shizuku.privileged.api", intent.getPackage());
        assertSame(callback, intent.getBundleExtra("data").getBinder("binder"));
        assertFalse(intent.hasExtra("auth"));
    }
}
