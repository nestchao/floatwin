package com.local.floatwin;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

public final class Launcher {

    public static final int WINDOWING_MODE_FREEFORM = 5;

    public interface Callback {
        void done(boolean ok, String message);
    }

    private Launcher() {
    }

    public static void open(final Context context, final AppRepo.App app, final boolean useRoot,
                            final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                if (useRoot) {
                    error = openWithRoot(context, app);
                } else {
                    try {
                        openDirect(context, app);
                    } catch (Throwable t) {
                        android.util.Log.d("floatwin", "direct failed: " + t, t);
                        error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                    }
                }
                final String message = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.done(message == null, message == null ? "" : message);
                    }
                });
            }
        }, "floatwin-launch").start();
    }

    private static String openWithRoot(Context context, AppRepo.App app) {
        String cmd = "am start --windowingMode " + WINDOWING_MODE_FREEFORM
                + " -n " + app.flatten() + " --user 0";

        Shell.Result r = Shell.su(cmd, 20000);
        String out = r.out == null ? "" : r.out;
        String lower = out.toLowerCase();
        if (lower.contains("exception") || lower.contains("error type")
                || lower.contains("does not exist") || lower.contains("permission denial")) {
            return out;
        }
        if (r.code == -1) {
            return "没拿到 Root 权限（KernelSU 里允许本应用后重试）";
        }
        if (r.code != 0) {
            return out.isEmpty() ? ("退出码 " + r.code) : out;
        }
        return null;
    }

    private static void openDirect(Context context, AppRepo.App app) throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(app.component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ActivityOptions options = ActivityOptions.makeBasic();
        Method setter = ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class);
        setter.invoke(options, WINDOWING_MODE_FREEFORM);
        context.startActivity(intent, options.toBundle());
    }

    public static boolean hasRoot() {
        Shell.sh("id", 5000);
        Shell.Result r = Shell.su("id", 30000);
        return r.code == 0 && r.out.contains("uid=0");
    }

    public static boolean grantOverlay(Context context) {
        String pkg = context.getPackageName();
        Shell.Result r = Shell.su("appops set " + pkg + " SYSTEM_ALERT_WINDOW allow", 12000);
        return r.code == 0;
    }
}
