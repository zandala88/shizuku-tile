package io.github.local.shizukutoggle;

import org.junit.Test;
import static org.junit.Assert.*;

public class ToggleStateTest {
    @Test public void stoppedStartsButDoesNotBecomeRunningUntilObserved() {
        ToggleState state = new ToggleState();
        state.observe(ToggleState.Actual.STOPPED, 100);
        assertEquals(ToggleState.Command.START, state.begin(true, 100));
        assertEquals(ToggleState.Actual.STOPPED, state.actual());
        assertEquals(ToggleState.Command.START, state.pending());
        state.observe(ToggleState.Actual.RUNNING, 900);
        assertEquals(ToggleState.Actual.RUNNING, state.actual());
        assertNull(state.pending());
    }

    @Test public void runningStopsOnlyAfterActualStopIsObserved() {
        ToggleState state = new ToggleState();
        state.observe(ToggleState.Actual.RUNNING, 10);
        assertEquals(ToggleState.Command.STOP, state.begin(true, 20));
        state.observe(ToggleState.Actual.UNKNOWN, 30);
        assertEquals(ToggleState.Command.STOP, state.pending());
        state.observe(ToggleState.Actual.STOPPED, 40);
        assertNull(state.pending());
    }

    @Test public void repeatedTapsDuringStartDoNotSendAnotherCommand() {
        ToggleState state = new ToggleState();
        state.observe(ToggleState.Actual.STOPPED, 0);
        assertEquals(ToggleState.Command.START, state.begin(true, 0));
        assertNull(state.begin(true, 1));
        assertEquals(ToggleState.Command.START, state.pending());
    }

    @Test public void startTimeoutPreservesActualStateAndAllowsRetry() {
        ToggleState state = new ToggleState();
        state.observe(ToggleState.Actual.STOPPED, 0);
        state.begin(true, 0);
        state.observe(ToggleState.Actual.STOPPED, 59_999);
        assertNotNull(state.pending());
        state.observe(ToggleState.Actual.STOPPED, 60_000);
        assertNull(state.pending());
        assertEquals(ToggleState.Actual.STOPPED, state.actual());
        assertTrue(state.message().contains("超时"));
        assertEquals(ToggleState.Command.START, state.begin(true, 60_001));
    }

    @Test public void unknownStatusOrMissingAuthNeverSendsACommand() {
        ToggleState state = new ToggleState();
        assertNull(state.begin(true, 0));
        state.observe(ToggleState.Actual.STOPPED, 1);
        assertNull(state.begin(false, 2));
        assertNull(state.pending());
    }

    @Test public void dispatchFailureClearsPendingWithoutChangingActualStatus() {
        ToggleState state = new ToggleState();
        state.observe(ToggleState.Actual.RUNNING, 0);
        state.begin(true, 1);
        state.fail("广播发送失败");
        assertNull(state.pending());
        assertEquals(ToggleState.Actual.RUNNING, state.actual());
        assertEquals("广播发送失败", state.message());
    }

    @Test public void restoredOperationStillBlocksTapsAndCanComplete() {
        ToggleState state = new ToggleState();
        state.restore(ToggleState.Command.START, 60_000, 30_000);
        state.observe(ToggleState.Actual.STOPPED, 30_100);
        assertNull(state.begin(true, 30_101));
        state.observe(ToggleState.Actual.RUNNING, 30_200);
        assertNull(state.pending());
    }
}
