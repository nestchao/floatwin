package com.local.floatwin;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String KSU_PACKAGE = "me.weishu.kernelsu";

    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView overlayValue;
    private TextView overlayAction;
    private TextView rootValue;
    private TextView rootAction;
    private View overlayDot;
    private View rootDot;
    private TextView toggleBtn;
    private LinearLayout rootHintBox;

    private boolean rootKnown;
    private boolean rootOk;
    private boolean checkingRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        checkRoot(false);
    }

    /** Automation hook: am start ... --es action open --es pkg com.example */
    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getStringExtra("action");
        if (action == null) {
            return;
        }
        if ("start".equals(action)) {
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    ensureOverlayThenStart();
                }
            }, 300);
        } else if ("stop".equals(action)) {
            stopService(new Intent(this, BubbleService.class));
            Prefs.setBubbleOn(this, false);
            refresh();
        } else if ("open".equals(action)) {
            final String pkg = intent.getStringExtra("pkg");
            if (pkg == null) {
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    AppRepo.App target = null;
                    for (AppRepo.App app : AppRepo.get(MainActivity.this)) {
                        if (app.pkg.equals(pkg)) {
                            target = app;
                            break;
                        }
                    }
                    final AppRepo.App found = target;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (found == null) {
                                toast(R.string.toast_failed, "没有找到 " + pkg);
                                return;
                            }
                            Launcher.open(MainActivity.this, found, true, new Launcher.Callback() {
                                @Override
                                public void done(boolean ok, String message) {
                                    toast(ok ? R.string.toast_opened : R.string.toast_failed, message);
                                }
                            });
                        }
                    });
                }
            }, "floatwin-open-intent").start();
        }
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return Util.dp(this, v);
    }

    private int color(int res) {
        return getColor(res);
    }

    private TextView text(String value, float sizeSp, int color, boolean medium) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        if (medium) {
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return tv;
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(color(R.color.bg));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(26), dp(62), dp(26), dp(34));
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout logo = new FrameLayout(this);
        logo.setBackground(Util.round(color(R.color.ink), dp(13)));
        ImageView glyph = new ImageView(this);
        glyph.setImageResource(R.drawable.ic_window);
        glyph.setColorFilter(Color.WHITE);
        logo.addView(glyph, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        col.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView title = text(getString(R.string.app_name), 31, color(R.color.ink), true);
        title.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(20);
        col.addView(title, titleLp);

        TextView subtitle = text(getString(R.string.subtitle), 14, color(R.color.muted), false);
        subtitle.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(8);
        col.addView(subtitle, subLp);

        // ---- status card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(18), dp(4), dp(18), dp(4));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(28);
        col.addView(card, cardLp);

        overlayDot = dot(color(R.color.muted));
        overlayValue = text(getString(R.string.state_off), 13, color(R.color.muted), false);
        overlayAction = text(getString(R.string.action_grant), 13, color(R.color.accent), true);
        overlayAction.setPadding(dp(8), dp(6), 0, dp(6));
        addRow(card, getString(R.string.perm_overlay), overlayDot, overlayValue, overlayAction,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openOverlaySettings();
                    }
                });

        View divider = new View(this);
        divider.setBackgroundColor(color(R.color.hairline));
        card.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f))));

        rootDot = dot(color(R.color.muted));
        rootValue = text(getString(R.string.state_unknown), 13, color(R.color.muted), false);
        rootAction = text(getString(R.string.action_check), 13, color(R.color.accent), true);
        rootAction.setPadding(dp(8), dp(6), 0, dp(6));
        addRow(card, getString(R.string.perm_root), rootDot, rootValue, rootAction,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkRoot(true);
                    }
                });

        // ---- root guidance
        rootHintBox = new LinearLayout(this);
        rootHintBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hintBoxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintBoxLp.topMargin = dp(16);
        col.addView(rootHintBox, hintBoxLp);

        TextView rootHint = text(getString(R.string.root_hint), 12.5f, color(R.color.muted), false);
        rootHint.setLineSpacing(dp(4), 1f);
        rootHintBox.addView(rootHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View ksuBtn = ghostButton(getString(R.string.open_ksu));
        LinearLayout.LayoutParams ksuLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        ksuLp.topMargin = dp(12);
        rootHintBox.addView(ksuBtn, ksuLp);
        ksuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openKsu();
            }
        });

        // ---- primary action
        toggleBtn = new TextView(this);
        toggleBtn.setText(getString(R.string.btn_open));
        toggleBtn.setTextSize(16);
        toggleBtn.setTextColor(Color.WHITE);
        toggleBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        toggleBtn.setGravity(Gravity.CENTER);
        toggleBtn.setBackgroundResource(R.drawable.sel_btn);
        toggleBtn.setClickable(true);
        toggleBtn.setFocusable(true);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        btnLp.topMargin = dp(24);
        col.addView(toggleBtn, btnLp);
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggle();
            }
        });

        TextView hint = text(getString(R.string.hint), 12.5f, color(R.color.muted), false);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(14);
        hintLp.leftMargin = dp(10);
        hintLp.rightMargin = dp(10);
        col.addView(hint, hintLp);

        TextView footer = text(getString(R.string.footer), 11, color(R.color.muted), false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = dp(44);
        col.addView(footer, footerLp);

        return scroll;
    }

    private View dot(int color) {
        View v = new View(this);
        v.setBackground(Util.circle(color));
        return v;
    }

    private View ghostButton(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setTextColor(color(R.color.ink));
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundResource(R.drawable.sel_btn_ghost);
        tv.setPadding(dp(18), 0, dp(18), 0);
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    private void addRow(LinearLayout parent, String label, View dot, TextView value,
                        TextView action, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(15), 0, dp(15));
        row.setClickable(true);

        row.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        TextView labelView = text(label, 15, color(R.color.ink), false);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.leftMargin = dp(12);
        row.addView(labelView, labelLp);

        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        row.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLp.leftMargin = dp(10);
        row.addView(action, actionLp);

        row.setOnClickListener(listener);
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ---------------------------------------------------------------- state

    private void refresh() {
        boolean overlay = Settings.canDrawOverlays(this);
        overlayValue.setText(overlay ? R.string.state_on : R.string.state_off);
        overlayValue.setTextColor(overlay ? color(R.color.ok) : color(R.color.muted));
        ((android.graphics.drawable.GradientDrawable) overlayDot.getBackground())
                .setColor(overlay ? color(R.color.ok) : color(R.color.muted));
        overlayAction.setVisibility(overlay ? View.GONE : View.VISIBLE);

        boolean running = BubbleService.isRunning;
        toggleBtn.setText(running ? R.string.btn_close : R.string.btn_open);
        toggleBtn.setBackgroundResource(running ? R.drawable.sel_btn_ghost : R.drawable.sel_btn);
        toggleBtn.setTextColor(running ? color(R.color.ink) : Color.WHITE);

        updateRootRow();
    }

    private void updateRootRow() {
        if (!rootKnown) {
            rootValue.setText(R.string.state_unknown);
            rootValue.setTextColor(color(R.color.muted));
            ((android.graphics.drawable.GradientDrawable) rootDot.getBackground())
                    .setColor(color(R.color.muted));
            rootHintBox.setVisibility(View.VISIBLE);
            return;
        }
        rootValue.setText(rootOk ? R.string.state_ok : R.string.state_no);
        rootValue.setTextColor(rootOk ? color(R.color.ok) : color(R.color.muted));
        ((android.graphics.drawable.GradientDrawable) rootDot.getBackground())
                .setColor(rootOk ? color(R.color.ok) : color(R.color.muted));
        rootAction.setVisibility(rootOk ? View.GONE : View.VISIBLE);
        rootHintBox.setVisibility(rootOk ? View.GONE : View.VISIBLE);
    }

    private void checkRoot(final boolean manual) {
        if (checkingRoot) {
            return;
        }
        checkingRoot = true;
        if (manual) {
            rootValue.setText(R.string.state_checking);
            rootValue.setTextColor(color(R.color.muted));
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    ok = Launcher.hasRoot();
                } catch (Throwable ignored) {
                }
                final boolean granted = ok;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        checkingRoot = false;
                        rootKnown = true;
                        rootOk = granted;
                        refresh();
                        if (manual) {
                            toast(granted ? R.string.toast_root_ok : R.string.toast_root_no);
                        }
                    }
                });
            }
        }, "floatwin-root").start();
    }

    // ---------------------------------------------------------------- actions

    private void onToggle() {
        if (BubbleService.isRunning) {
            Prefs.setBubbleOn(this, false);
            Watchdog.stop(this);
            stopService(new Intent(this, BubbleService.class));
            toast(R.string.toast_bubble_off);
            refresh();
            return;
        }
        ensureOverlayThenStart();
    }

    private void ensureOverlayThenStart() {
        final boolean canUseRoot = rootKnown && rootOk;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = Settings.canDrawOverlays(MainActivity.this);
                if (!ok && canUseRoot) {
                    Launcher.grantOverlay(MainActivity.this);
                    try {
                        Thread.sleep(180);
                    } catch (InterruptedException ignored) {
                    }
                    ok = Settings.canDrawOverlays(MainActivity.this);
                }
                final boolean granted = ok;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (granted) {
                            startBubble();
                        } else {
                            toast(R.string.toast_overlay_needed);
                            openOverlaySettings();
                        }
                    }
                });
            }
        }, "floatwin-overlay").start();
    }

    private void startBubble() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            try {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            } catch (Throwable ignored) {
            }
        }
        Intent intent = new Intent(this, BubbleService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Prefs.setBubbleOn(this, true);
        toast(R.string.toast_bubble_on);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 400);
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Throwable ignored) {
            }
        }
    }

    private void openKsu() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(KSU_PACKAGE);
            if (intent == null) {
                toast(R.string.toast_no_ksu);
                return;
            }
            startActivity(intent);
        } catch (Throwable t) {
            toast(R.string.toast_no_ksu);
        }
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private void toast(int res, String suffix) {
        Toast.makeText(this, getString(res, suffix), Toast.LENGTH_LONG).show();
    }
}
