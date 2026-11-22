package com.local.floatwin;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppRepo {

    public static final class App {
        public final String pkg;
        public final String label;
        public final ComponentName component;
        public final Drawable icon;

        App(String pkg, String label, ComponentName component, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.component = component;
            this.icon = icon;
        }

        public String flatten() {
            return component.flattenToShortString();
        }
    }

    private static volatile List<App> cache;

    private AppRepo() {
    }

    public static List<App> get(Context context) {
        List<App> local = cache;
        if (local != null) {
            return local;
        }
        synchronized (AppRepo.class) {
            if (cache != null) {
                return cache;
            }
            cache = load(context);
            return cache;
        }
    }

    public static void invalidate() {
        cache = null;
    }

    private static List<App> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved;
        try {
            resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        } catch (Throwable t) {
            resolved = pm.queryIntentActivities(intent, 0);
        }

        List<App> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String self = context.getPackageName();

        for (ResolveInfo ri : resolved) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ai.packageName == null || ai.name == null) {
                continue;
            }
            if (self.equals(ai.packageName)) {
                continue;
            }
            if (!seen.add(ai.packageName)) {
                continue;
            }
            String label;
            try {
                CharSequence cs = ri.loadLabel(pm);
                label = cs == null ? ai.packageName : cs.toString();
            } catch (Throwable t) {
                label = ai.packageName;
            }
            Drawable icon = null;
            try {
                icon = ri.loadIcon(pm);
            } catch (Throwable ignored) {
            }
            apps.add(new App(ai.packageName, label, new ComponentName(ai.packageName, ai.name), icon));
        }

        Collections.sort(apps, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }
}
