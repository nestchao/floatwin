package com.local.floatwin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.bubbleOn(context)) {
            return;
        }
        if (!Settings.canDrawOverlays(context)) {
            return;
        }
        Intent service = new Intent(context, BubbleService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Throwable ignored) {
            // background start may be restricted on some builds
        }
    }
}
