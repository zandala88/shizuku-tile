package io.github.local.shizukutoggle;

import android.service.quicksettings.Tile;
import android.widget.EditText;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TileAndSettingsTest {
    @Test public void commandErrorsRemainVisibleWithKnownRunningOrStoppedService() {
        for (ToggleState.Actual actual : new ToggleState.Actual[]{ToggleState.Actual.RUNNING, ToggleState.Actual.STOPPED}) {
            ToggleState state = new ToggleState();
            state.observe(actual, 0);
            state.fail("广播发送失败，请打开 Shizuku 检查");
            assertEquals("广播发送失败，请打开 Shizuku 检查",
                    MainActivity.detailText(new ShizukuController.ViewState(state, true, true)));
        }
    }

    @Test public void missingAuthGuidanceRemainsVisibleEvenWhenServiceIsRunning() {
        ToggleState state = new ToggleState();
        state.observe(ToggleState.Actual.RUNNING, 0);
        assertEquals("请先打开应用填写 auth",
                MainActivity.detailText(new ShizukuController.ViewState(state, false, true)));
    }
    @Test public void configuredAuthIsCollapsedAndCanBeExpanded() {
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", 0)
                .edit().putString("auth", "fixture-configured").commit();
        try (ActivityController<MainActivity> activity = Robolectric.buildActivity(MainActivity.class).setup()) {
            View details = activity.get().findViewById(R.id.auth_details);
            assertNotNull("Auth settings should have a collapsible details section", details);
            assertEquals(View.GONE, details.getVisibility());
            activity.get().findViewById(R.id.auth_header).performClick();
            assertEquals(View.VISIBLE, details.getVisibility());
        }
    }
    @Test public void unconfiguredTileRemainsClickableToOpenSetup() {
        Tile tile = org.robolectric.util.ReflectionHelpers.callConstructor(Tile.class);
        ShizukuTileService.applyState(tile, new ShizukuController.ViewState(new ToggleState(), false, false));
        assertEquals(Tile.STATE_INACTIVE, tile.getState());
        assertTrue(tile.getSubtitle().toString().contains("auth"));
    }
    @Test public void tileDoesNotShowActiveWhileStartIsOnlyPending() {
        ToggleState state = new ToggleState();
        state.observe(ToggleState.Actual.STOPPED, 0);
        state.begin(true, 1);
        Tile tile = org.robolectric.util.ReflectionHelpers.callConstructor(Tile.class);
        ShizukuTileService.applyState(tile, new ShizukuController.ViewState(state, true, true));
        assertEquals(Tile.STATE_INACTIVE, tile.getState());
        assertTrue(tile.getLabel().toString().contains("正在启动"));
        assertTrue(tile.getSubtitle().toString().contains("Shizuku"));
        state.observe(ToggleState.Actual.RUNNING, 2);
        ShizukuTileService.applyState(tile, new ShizukuController.ViewState(state, true, true));
        assertEquals(Tile.STATE_ACTIVE, tile.getState());
        assertEquals("Shizuku", tile.getLabel());
    }

    @Test public void stoppingTileKeepsRunningColorAndShowsProgressInMainLabel() {
        ToggleState state = new ToggleState();
        state.observe(ToggleState.Actual.RUNNING, 0);
        state.begin(true, 1);
        Tile tile = org.robolectric.util.ReflectionHelpers.callConstructor(Tile.class);
        ShizukuTileService.applyState(tile, new ShizukuController.ViewState(state, true, true));
        assertEquals(Tile.STATE_ACTIVE, tile.getState());
        assertTrue(tile.getLabel().toString().contains("正在停止"));
    }

    @Test public void settingsSavesTrimmedAuthAndRejectsEmptyReplacement() {
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", 0)
                .edit().putString("auth", "fixture-before").commit();
        try (ActivityController<MainActivity> activity = Robolectric.buildActivity(MainActivity.class).setup()) {
            EditText input = activity.get().findViewById(R.id.auth_input);
            assertNotNull("Settings must expose the single auth field", input);
            assertEquals("fixture-before", input.getText().toString());
            input.setText(" replacement \n");
            activity.get().findViewById(R.id.save_auth).performClick();
            assertEquals("replacement", ShizukuController.get(activity.get()).token());
            input.setText(" ");
            activity.get().findViewById(R.id.save_auth).performClick();
            assertNotNull(input.getError());
            assertEquals("replacement", ShizukuController.get(activity.get()).token());
        }
    }

    @Test @Config(sdk = 28) public void settingsAlsoOpensOnAndroid9() {
        try (ActivityController<MainActivity> activity = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertNotNull(activity.get().findViewById(R.id.auth_input));
            assertNotNull(activity.get().findViewById(R.id.add_tile));
        }
    }
}
