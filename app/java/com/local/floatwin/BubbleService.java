package com.local.floatwin;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

public class BubbleService extends Service {

    public static volatile boolean isRunning;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private FrameLayout bubble;
    private WindowManager.LayoutParams params;
    private PanelWindow panel;

    private int screenW;
    private int screenH;
    private int size;

    private float downRawX;
    private float downRawY;
    private int fromX;
    private int fromY;
    private boolean dragged;
    private long downAt;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int[] screen = Util.screen(this);
        screenW = screen[0];
        screenH = screen[1];
        size = Util.dp(this, 54);

        startForegroundNotification();
        addBubble();
        Watchdog.start(getApplicationContext());

        new Thread(new Runnable() {
            @Override
            public void run() {
                // warm the app list so the panel opens instantly
                AppRepo.get(getApplicationContext());
            }
        }, "floatwin-preload").start();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 主界面被清掉时，保持悬浮球存活；守护进程会在进程被杀后把它拉起来
        super.onTaskRemoved(rootIntent);
    }

    private void startForegroundNotification() {
        String channelId = "bubble";
        if (Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, getString(R.string.notif_channel),
                    android.app.NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            channel.setDescription(getString(R.string.notif_text));
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(this, channelId)
                : new android.app.Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setOngoing(true)
                .setShowWhen(false);
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                this, 0, open,
                Build.VERSION.SDK_INT >= 23
                        ? android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                        : android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pi);

        android.app.Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
    }

    private void addBubble() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg_bubble);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_window);
        int iconSize = Util.dp(this, 25);
        root.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));

        bubble = root;
        params = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Prefs.bubbleX(this, screenW - size - Util.dp(this, 10));
        params.y = Prefs.bubbleY(this, (int) (screenH * 0.52f));

        bubble.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return onBubbleTouch(event);
            }
        });

        windowManager.addView(bubble, params);
    }

    private boolean onBubbleTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                fromX = params.x;
                fromY = params.y;
                dragged = false;
                downAt = SystemClock.uptimeMillis();
                bubble.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragged && Math.abs(dx) + Math.abs(dy) > Util.dp(this, 6)) {
                    dragged = true;
                }
                if (dragged) {
                    params.x = Util.clamp((int) (fromX + dx), 0, Math.max(0, screenW - size));
                    params.y = Util.clamp((int) (fromY + dy), 0, Math.max(0, screenH - size));
                    safeUpdate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                bubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                if (dragged) {
                    snapToEdge();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (SystemClock.uptimeMillis() - downAt > 480) {
                        openMain();
                    } else {
                        openPanel();
                    }
                }
                return true;
            default:
                return false;
        }
    }

    private void safeUpdate() {
        try {
            windowManager.updateViewLayout(bubble, params);
        } catch (Throwable ignored) {
        }
    }

    private void snapToEdge() {
        final int margin = Util.dp(this, 6);
        int target = (params.x + size / 2 < screenW / 2) ? margin : screenW - size - margin;
        final int startX = params.x;
        int delta = target - startX;
        if (Math.abs(delta) < 2) {
            Prefs.setBubblePos(this, params.x, params.y);
            return;
        }
        android.animation.ValueAnimator animator =
                android.animation.ValueAnimator.ofInt(0, delta);
        animator.setDuration(Math.min(260, 120 + Math.abs(delta) / 4));
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                params.x = startX + (int) animation.getAnimatedValue();
                safeUpdate();
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                Prefs.setBubblePos(BubbleService.this, params.x, params.y);
            }
        });
        animator.start();
    }

    private void openPanel() {
        if (panel != null && panel.isShowing()) {
            panel.hide();
            return;
        }
        panel = new PanelWindow(this, windowManager, new PanelWindow.Listener() {
            @Override
            public void onPick(AppRepo.App app) {
                launch(app);
            }
        });
        panel.show();
    }

    private void openMain() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void launch(final AppRepo.App app) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Launcher.open(getApplicationContext(), app, true, new Launcher.Callback() {
                    @Override
                    public void done(boolean ok, String message) {
                        if (ok) {
                            Toast.makeText(getApplicationContext(), R.string.toast_opened,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplicationContext(),
                                    getString(R.string.toast_failed, message),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }, 180);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        Prefs.setBubbleOn(this, false);
        if (panel != null) {
            panel.hideImmediate();
            panel = null;
        }
        if (bubble != null) {
            try {
                windowManager.removeView(bubble);
            } catch (Throwable ignored) {
            }
            bubble = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
