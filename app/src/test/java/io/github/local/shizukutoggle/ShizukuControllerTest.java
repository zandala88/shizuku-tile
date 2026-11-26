package io.github.local.shizukutoggle;

import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
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
public class ShizukuControllerTest {
    private Application app;
    private ShizukuController controller;
    private final Runnable observer = () -> { };

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        PackageInfo manager = new PackageInfo();
        manager.packageName = "moe.shizuku.privileged.api";
        shadowOf(app.getPackageManager()).installPackage(manager);
        app.getSharedPreferences("settings", 0).edit().clear().putString("auth", "fixture-token").commit();
        controller = new ShizukuController(app);
    }
    @After public void tearDown() { controller.removeObserver(observer); }

    @Test public void tileTapsSendOneAuthenticatedStartThenWaitForRealStatus() throws Exception {
        controller.addObserver(observer);
        respond(null);
        confirmInitialStop();
        controller.toggle();
        controller.toggle();
        List<Intent> starts = commands("moe.shizuku.privileged.api.START");
        assertEquals(1, starts.size());
        assertEquals("fixture-token", starts.get(0).getStringExtra("auth"));
        assertEquals(ToggleState.Actual.STOPPED, controller.view().actual);
        assertEquals(ToggleState.Command.START, controller.view().pending);
        respond(new Binder());
        assertEquals(ToggleState.Actual.RUNNING, controller.view().actual);
        assertNull(controller.view().pending);
    }

    @Test public void restoredPendingStartDoesNotSendDuplicateAfterProcessRecreation() throws Exception {
        controller.addObserver(observer);
        respond(null);
        confirmInitialStop();
        controller.toggle();
        controller.removeObserver(observer);
        controller = new ShizukuController(app);
        controller.addObserver(observer);
        respond(null);
        controller.toggle();
        assertEquals(1, commands("moe.shizuku.privileged.api.START").size());
        assertEquals(ToggleState.Command.START, controller.view().pending);
    }

    @Test public void closingLastObserverStopsPollingAndReopeningClearsStaleStatus() throws Exception {
        controller.addObserver(observer);
        respond(new Binder());
        controller.removeObserver(observer);
        int sent = shadowOf(app).getBroadcastIntents().size();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertEquals(sent, shadowOf(app).getBroadcastIntents().size());
        controller.addObserver(observer);
        assertEquals(ToggleState.Actual.UNKNOWN, controller.view().actual);
        assertFalse(controller.view().canToggle);
    }

    @Test public void blankAuthIsRejectedWithoutOverwritingSavedValue() {
        assertFalse(controller.saveToken("  \n "));
        assertEquals("fixture-token", controller.token());
        assertTrue(controller.saveToken("  changed-token \n"));
        assertEquals("changed-token", controller.token());
    }

    @Test public void pendingFeedbackIsPublishedBeforeStartBroadcastIsSent() throws Exception {
        controller.addObserver(observer);
        respond(null);
        confirmInitialStop();
        java.util.ArrayList<Integer> commandCountsAtFeedback = new java.util.ArrayList<>();
        Runnable feedback = () -> {
            if (controller.view().pending != null)
                commandCountsAtFeedback.add(commands("moe.shizuku.privileged.api.START").size());
        };
        controller.addObserver(feedback);
        try {
            controller.toggle();
            assertFalse(commandCountsAtFeedback.isEmpty());
            assertEquals(Integer.valueOf(0), commandCountsAtFeedback.get(0));
        } finally { controller.removeObserver(feedback); }
    }

    @Test public void pendingOperationProbesAgainWithin250Milliseconds() throws Exception {
        controller.addObserver(observer);
        respond(null);
        confirmInitialStop();
        controller.toggle();
        respond(null);
        int before = shadowOf(app).getBroadcastIntents().size();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        assertEquals(before + 1, shadowOf(app).getBroadcastIntents().size());
    }

    @Test public void noStatusReplyRemainsUnknownAndCannotTriggerAStart() {
        controller.addObserver(observer);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        controller.toggle();
        assertEquals(ToggleState.Actual.UNKNOWN, controller.view().actual);
        assertFalse(controller.view().canToggle);
        assertTrue(commands("moe.shizuku.privileged.api.START").isEmpty());
    }

    @Test public void runningTileSendsAuthenticatedStop() throws Exception {
        controller.addObserver(observer);
        TestServerBinder server = new TestServerBinder();
        respond(server);
        controller.toggle();
        List<Intent> stops = commands("moe.shizuku.privileged.api.STOP");
        assertEquals(1, stops.size());
        assertEquals("fixture-token", stops.get(0).getStringExtra("auth"));
        server.alive = false;
        respond(server);
        assertEquals(ToggleState.Actual.STOPPED, controller.view().actual);
        assertNull(controller.view().pending);
    }

    @Test public void installationErrorClearsAfterManagerBecomesAvailable() throws Exception {
        shadowOf(app.getPackageManager()).removePackage("moe.shizuku.privileged.api");
        controller.addObserver(observer);
        assertTrue(controller.view().message.contains("未安装"));
        controller.removeObserver(observer);
        PackageInfo manager = new PackageInfo();
        manager.packageName = "moe.shizuku.privileged.api";
        shadowOf(app.getPackageManager()).installPackage(manager);
        controller.addObserver(observer);
        respond(new Binder());
        assertEquals(ToggleState.Actual.RUNNING, controller.view().actual);
        assertFalse(controller.view().message.contains("未安装"));
    }

    private void confirmInitialStop() throws Exception {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750));
        respond(null);
    }

    private static final class TestServerBinder extends Binder {
        boolean alive = true;
        @Override public boolean pingBinder() { return alive; }
    }

    private void respond(IBinder remote) throws Exception {
        List<Intent> broadcasts = shadowOf(app).getBroadcastIntents();
        Intent request = broadcasts.get(broadcasts.size() - 1);
        assertEquals("rikka.shizuku.intent.action.REQUEST_BINDER", request.getAction());
        ShizukuBridgeTest.reply(request.getBundleExtra("data").getBinder("binder"), remote);
        shadowOf(Looper.getMainLooper()).idle();
    }
    private List<Intent> commands(String action) {
        return shadowOf(app).getBroadcastIntents().stream().filter(i -> action.equals(i.getAction())).toList();
    }
}
