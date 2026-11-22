package com.local.floatwin;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String NAME = "floatwin";

    private Prefs() {
    }

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean bubbleOn(Context c) {
        return get(c).getBoolean("bubble_on", false);
    }

    public static void setBubbleOn(Context c, boolean on) {
        get(c).edit().putBoolean("bubble_on", on).apply();
    }

    public static int bubbleX(Context c, int def) {
        return get(c).getInt("bubble_x", def);
    }

    public static int bubbleY(Context c, int def) {
        return get(c).getInt("bubble_y", def);
    }

    public static void setBubblePos(Context c, int x, int y) {
        get(c).edit().putInt("bubble_x", x).putInt("bubble_y", y).apply();
    }
}
