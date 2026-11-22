package com.local.floatwin;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public final class Util {

    private Util() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable round(int color, float radiusPx, int strokeWidth, int strokeColor) {
        GradientDrawable d = round(color, radiusPx);
        if (strokeWidth > 0) {
            d.setStroke(strokeWidth, strokeColor);
        }
        return d;
    }

    public static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    public static Drawable tint(Context c, int resId, int color) {
        Drawable d = c.getDrawable(resId);
        if (d == null) {
            return null;
        }
        d = d.mutate();
        d.setTint(color);
        return d;
    }

    public static int[] screen(Context c) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return new int[]{m.widthPixels, m.heightPixels};
    }

    public static int clamp(int v, int min, int max) {
        if (v < min) {
            return min;
        }
        return Math.min(v, max);
    }
}
