package io.github.local.shizukutoggle;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class ShizukuTileService extends TileService {
    private ShizukuController controller;
    private final Runnable observer = this::render;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable animate = this::render;
    private boolean listening;

    @Override public void onCreate() {
        super.onCreate();
        controller = ShizukuController.get(this);
    }

    @Override public void onStartListening() {
        super.onStartListening();
        listening = true;
        controller.addObserver(observer);
    }

    @Override public void onStopListening() {
        listening = false;
        main.removeCallbacks(animate);
        controller.removeObserver(observer);
        super.onStopListening();
    }

    @Override public void onDestroy() {
        listening = false;
        main.removeCallbacks(animate);
        if (controller != null) controller.removeObserver(observer);
        super.onDestroy();
    }

    @Override public void onClick() {
        super.onClick();
        if (!controller.view().configured) { openSettings(); return; }
        if (isLocked()) unlockAndRun(controller::toggle);
        else controller.toggle();
    }

    private void render() {
        main.removeCallbacks(animate);
        Tile tile = getQsTile();
        if (tile == null) return;
        ShizukuController.ViewState state = controller.view();
        tile.setIcon(state.pending != null ? TileFeedback.icon(this, SystemClock.uptimeMillis())
                : Icon.createWithResource(this, R.drawable.ic_tile));
        applyState(tile, state);
        tile.updateTile();
        if (listening && state.pending != null) main.postDelayed(animate, TileFeedback.FRAME_MILLIS);
    }

    static void applyState(Tile tile, ShizukuController.ViewState state) {
        boolean busy = state.pending != null;
        tile.setLabel(busy ? state.pending == ToggleState.Command.START ? "正在启动…" : "正在停止…" : "Shizuku");
        // Unconfigured tiles stay clickable so onClick can open setup.
        // During a command, keep the actual color and let the controller suppress repeated taps.
        tile.setState(!state.configured ? Tile.STATE_INACTIVE : busy
                ? state.pending == ToggleState.Command.STOP ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE
                : !state.canToggle ? Tile.STATE_UNAVAILABLE
                : state.actual == ToggleState.Actual.RUNNING ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setContentDescription("Shizuku，" + state.message);
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(busy ? "Shizuku · 请稍候" : state.message);
        if (Build.VERSION.SDK_INT >= 30) tile.setStateDescription(state.message);
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void openSettings() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            // PendingIntent overload exists only on Android 14+. This branch is pre-14 only.
            startActivityAndCollapse(intent);
        }
    }
}
