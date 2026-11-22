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
        // 临时 root（KernelSU LKM 等）重启后会消失，这时不要起一个"点不开应用"的假悬浮球。
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean root = false;
                try {
                    root = Launcher.hasRoot();
                } catch (Throwable ignored) {
                }
                if (root) {
                    try {
                        Intent service = new Intent(app, BubbleService.class);
                        if (Build.VERSION.SDK_INT >= 26) {
                            app.startForegroundService(service);
                        } else {
                            app.startService(service);
                        }
                    } catch (Throwable ignored) {
                        // background start may be restricted on some builds
                    }
                } else {
                    Prefs.setBubbleOn(app, false);
                }
                pending.finish();
            }
        }, "floatwin-boot").start();
    }
}
