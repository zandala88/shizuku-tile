package io.github.local.shizukutoggle;

import android.app.Application;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import java.time.Duration;
import java.util.ArrayList;
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
public class ShizukuBridgeTest {
    private Application app;
    private ShizukuBridge bridge;
    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        bridge = new ShizukuBridge(app);
    }
    @After public void tearDown() { bridge.close(); }

    @Test public void liveBinderCallbackReportsRunning() throws Exception {
        List<ToggleState.Actual> results = new ArrayList<>();
        bridge.probe(results::add);
        reply(lastCallback(), new Binder());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of(ToggleState.Actual.RUNNING), results);
    }

    @Test public void initialNullBinderNeedsASecondReplyBeforeReportingStopped() throws Exception {
        List<ToggleState.Actual> results = new ArrayList<>();
        bridge.probe(results::add);
        reply(lastCallback(), null);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of(ToggleState.Actual.UNKNOWN), results);
        bridge.probe(results::add);
        reply(lastCallback(), null);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of(ToggleState.Actual.UNKNOWN, ToggleState.Actual.STOPPED), results);
    }

    @Test public void coldManagerNullThenLiveBinderNeverReportsStopped() throws Exception {
        List<ToggleState.Actual> results = new ArrayList<>();
        bridge.probe(results::add);
        reply(lastCallback(), null);
        shadowOf(Looper.getMainLooper()).idle();
        bridge.probe(results::add);
        reply(lastCallback(), new Binder());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of(ToggleState.Actual.UNKNOWN, ToggleState.Actual.RUNNING), results);
    }

    @Test public void nullManagerReplyDoesNotDiscardAKnownLiveServerBinder() throws Exception {
        List<ToggleState.Actual> results = new ArrayList<>();
        bridge.probe(results::add);
        reply(lastCallback(), new Binder());
        shadowOf(Looper.getMainLooper()).idle();
        bridge.probe(results::add);
        reply(lastCallback(), null);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of(ToggleState.Actual.RUNNING, ToggleState.Actual.RUNNING), results);
    }

    @Test public void serverDyingDuringLinkIsHandledWithoutUnlinkingAnUnregisteredListener() throws Exception {
        IBinder dying = (IBinder) java.lang.reflect.Proxy.newProxyInstance(
                IBinder.class.getClassLoader(), new Class<?>[]{IBinder.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "pingBinder", "isBinderAlive" -> true;
                        case "linkToDeath" -> throw new android.os.RemoteException("Server died during link");
                        case "unlinkToDeath" -> throw new java.util.NoSuchElementException("Never linked");
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                });
        List<ToggleState.Actual> results = new ArrayList<>();
        bridge.probe(results::add);
        reply(lastCallback(), dying);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of(ToggleState.Actual.STOPPED), results);
    }

    @Test public void missingCallbackNeverPretendsServiceIsStopped() {
        List<ToggleState.Actual> results = new ArrayList<>();
        bridge.probe(results::add);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals(List.of(ToggleState.Actual.UNKNOWN), results);
    }

    @Test public void lateCallbackCannotOverwriteNewerProbe() throws Exception {
        List<ToggleState.Actual> results = new ArrayList<>();
        bridge.probe(results::add);
        IBinder oldCallback = lastCallback();
        bridge.probe(results::add);
        reply(lastCallback(), new Binder());
        reply(oldCallback, null);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of(ToggleState.Actual.RUNNING), results);
    }

    @Test public void closingProbeCancelsTimeoutAndLateResults() throws Exception {
        List<ToggleState.Actual> results = new ArrayList<>();
        bridge.probe(results::add);
        IBinder callback = lastCallback();
        bridge.close();
        reply(callback, new Binder());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertTrue(results.isEmpty());
    }

    private IBinder lastCallback() {
        List<Intent> intents = shadowOf(app).getBroadcastIntents();
        assertFalse("A status probe must send a Binder request", intents.isEmpty());
        return intents.get(intents.size() - 1).getBundleExtra("data").getBinder("binder");
    }
    static void reply(IBinder callback, IBinder server) throws Exception {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeStrongBinder(server);
            parcel.writeString("/installed/shizuku.apk");
            callback.transact(1, parcel, null, IBinder.FLAG_ONEWAY);
        } finally { parcel.recycle(); }
    }
}
