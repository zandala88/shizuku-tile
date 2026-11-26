package io.github.local.shizukutoggle;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import java.time.Duration;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UsbStopOptionTest {
    private Application app;
    private ShizukuController controller;
    private final Runnable observer = () -> { };
    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        PackageInfo manager = new PackageInfo();
        manager.packageName = "moe.shizuku.privileged.api";
        shadowOf(app.getPackageManager()).installPackage(manager);
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        app.getSharedPreferences("settings", 0).edit().clear().putString("auth", "fixture-token").commit();
        Settings.Global.putInt(app.getContentResolver(), Settings.Global.ADB_ENABLED, 1);
        controller = new ShizukuController(app);
    }
    @After public void tearDown() {
        controller.setCloseUsbOnStop(false);
        controller.removeObserver(observer);
    }

    @Test public void optionDefaultsOffAndOrdinaryStopLeavesUsbEnabled() throws Exception {
        assertFalse(controller.closeUsbOnStop());
        Server server = runningServer();
        controller.toggle();
        server.alive = false;
        respond(server);
        assertEquals(1, usb());
    }

    @Test public void enabledStopDisablesUsbOnlyAfterActualStopIsConfirmed() throws Exception {
        enable();
        Server server = runningServer();
        controller.toggle();
        assertEquals(1, usb());
        respond(server);
        assertEquals(1, usb());
        server.alive = false;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        respond(server);
        assertEquals(0, usb());
        assertEquals(ToggleState.Actual.STOPPED, controller.view().actual);
        assertNull(controller.view().pending);
    }

    @Test public void externalStopNeverDisablesUsb() throws Exception {
        enable();
        Server server = runningServer();
        server.alive = false;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750));
        respond(server);
        assertEquals(1, usb());
    }

    @Test public void startNeverDisablesUsbEvenWithOptionEnabled() throws Exception {
        enable();
        controller.addObserver(observer);
        respond(null);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        respond(null);
        controller.toggle();
        respond(new Binder());
        assertEquals(1, usb());
    }

    @Test public void preferencePersistsAndCannotBeEnabledWithoutPermission() {
        enable();
        assertTrue(new ShizukuController(app).closeUsbOnStop());
        assertTrue(controller.setCloseUsbOnStop(false));
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        assertFalse(controller.setCloseUsbOnStop(true));
        assertFalse(controller.closeUsbOnStop());
    }

    @Test public void permissionLossDoesNotBlockStopButReportsUsbStillEnabled() throws Exception {
        enable();
        Server server = runningServer();
        controller.toggle();
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        server.alive = false;
        respond(server);
        assertEquals(ToggleState.Actual.STOPPED, controller.view().actual);
        assertNull(controller.view().pending);
        assertEquals(1, usb());
        assertTrue(controller.view().needsAttention);
        assertTrue(controller.view().message.contains("USB"));
        assertTrue(controller.view().message.contains("授权"));
    }

    @Test public void panelClosingStillFinishesRequestedUsbCleanupThenStopsPolling() throws Exception {
        enable();
        Server server = runningServer();
        controller.toggle();
        controller.removeObserver(observer);
        server.alive = false;
        respond(server);
        assertEquals(0, usb());
        int count = shadowOf(app).getBroadcastIntents().size();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals(count, shadowOf(app).getBroadcastIntents().size());
    }

    @Test public void disablingOptionCancelsPendingUsbCleanup() throws Exception {
        enable();
        Server server = runningServer();
        controller.toggle();
        controller.setCloseUsbOnStop(false);
        server.alive = false;
        respond(server);
        assertEquals(1, usb());
    }

    @Test public void enablingOptionAfterStopWasIssuedDoesNotRetroactivelyDisableUsb() throws Exception {
        Server server = runningServer();
        controller.toggle();
        enable();
        server.alive = false;
        respond(server);
        assertEquals(1, usb());
    }

    @Test public void persistedStopCleanupResumesAfterProcessRecreation() throws Exception {
        app.getSharedPreferences("settings", 0).edit()
                .putBoolean("close_usb_on_stop", true).putBoolean("pending_usb_cleanup", true)
                .putString("pending", "STOP").putLong("deadline", System.currentTimeMillis() + 60_000).commit();
        controller = new ShizukuController(app);
        controller.addObserver(observer);
        shadowOf(Looper.getMainLooper()).idle();
        respond(null);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        respond(null);
        assertEquals(0, usb());
        assertFalse(app.getSharedPreferences("settings", 0).getBoolean("pending_usb_cleanup", false));
    }

    @Test public void expiredStopCleanupNeverDisablesUsbLater() throws Exception {
        app.getSharedPreferences("settings", 0).edit()
                .putBoolean("close_usb_on_stop", true).putBoolean("pending_usb_cleanup", true)
                .putString("pending", "STOP").putLong("deadline", System.currentTimeMillis() - 1).commit();
        controller = new ShizukuController(app);
        controller.addObserver(observer);
        respond(null);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        respond(null);
        assertEquals(1, usb());
        assertFalse(app.getSharedPreferences("settings", 0).getBoolean("pending_usb_cleanup", false));
    }

    @Test public void stopCallbackAfterDeadlineNeverDisablesUsb() throws Exception {
        enable();
        Server server = runningServer();
        controller.toggle();
        ToggleState state = org.robolectric.util.ReflectionHelpers.getField(controller, "state");
        long time = System.currentTimeMillis();
        state.restore(ToggleState.Command.STOP, time - 1, time - 2);
        server.alive = false;
        respond(server);
        assertEquals(1, usb());
        assertTrue(controller.view().message.contains("超时"));
    }

    @Test public void failedStopBroadcastCannotTriggerUsbCleanupOnLaterDeath() throws Exception {
        controller = new ShizukuController(new ContextWrapper(app) {
            @Override public Context getApplicationContext() { return this; }
            @Override public void sendBroadcast(Intent intent) {
                if ("moe.shizuku.privileged.api.STOP".equals(intent.getAction())) throw new SecurityException("Rejected");
                super.sendBroadcast(intent);
            }
        });
        enable();
        Server server = runningServer();
        controller.toggle();
        assertNull(controller.view().pending);
        server.alive = false;
        respond(server);
        assertEquals(1, usb());
        assertTrue(controller.view().message.contains("失败"));
    }

    @Test public void failedDurableConsumptionSkipsPrivilegedUsbWrite() throws Exception {
        SharedPreferences real = app.getSharedPreferences("settings", 0);
        SharedPreferences failing = (SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("edit")) return method.invoke(real, args);
                    SharedPreferences.Editor editor = real.edit();
                    return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                            new Class<?>[]{SharedPreferences.Editor.class}, (ep, operation, values) -> {
                                if (operation.getName().equals("commit")) return false;
                                Object result = operation.invoke(editor, values);
                                return result instanceof SharedPreferences.Editor ? ep : result;
                            });
                });
        controller = new ShizukuController(new ContextWrapper(app) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { return failing; }
        });
        enable();
        Server server = runningServer();
        controller.toggle();
        server.alive = false;
        respond(server);
        assertEquals(1, usb());
        assertTrue(controller.view().message.contains("保存"));
    }

    private void enable() { assertTrue(controller.setCloseUsbOnStop(true)); }
    private int usb() { return Settings.Global.getInt(app.getContentResolver(), Settings.Global.ADB_ENABLED, -1); }
    private Server runningServer() throws Exception {
        controller.addObserver(observer);
        Server server = new Server();
        respond(server);
        return server;
    }
    private void respond(IBinder server) throws Exception {
        List<Intent> broadcasts = shadowOf(app).getBroadcastIntents();
        Intent request = broadcasts.get(broadcasts.size() - 1);
        assertEquals("rikka.shizuku.intent.action.REQUEST_BINDER", request.getAction());
        ShizukuBridgeTest.reply(request.getBundleExtra("data").getBinder("binder"), server);
        shadowOf(Looper.getMainLooper()).idle();
    }
    private static final class Server extends Binder {
        boolean alive = true;
        @Override public boolean pingBinder() { return alive; }
    }
}
