package com.local.floatwin;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.TileService;

public class Tile extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        update();
    }

    @Override
    public void onClick() {
        if (Prefs.bubbleOn(this)) {
            stopService(new Intent(this, BubbleService.class));
            Prefs.setBubbleOn(this, false);
        } else if (Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(this, BubbleService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Prefs.setBubbleOn(this, true);
        } else {
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(open);
            return;
        }
        update();
    }

    private void update() {
        if (getQsTile() == null) {
            return;
        }
        boolean on = Prefs.bubbleOn(this);
        getQsTile().setState(on
                ? android.service.quicksettings.Tile.STATE_ACTIVE
                : android.service.quicksettings.Tile.STATE_INACTIVE);
        getQsTile().setIcon(Icon.createWithResource(this, R.drawable.ic_window));
        getQsTile().setLabel(getString(R.string.app_name));
        getQsTile().updateTile();
    }
}
