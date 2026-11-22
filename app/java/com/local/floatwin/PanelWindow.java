package com.local.floatwin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PanelWindow {

    public interface Listener {
        void onPick(AppRepo.App app);
    }

    private final Context context;
    private final WindowManager windowManager;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private WindowManager.LayoutParams params;
    private FrameLayout panel;
    private EditText search;
    private AppAdapter adapter;
    private boolean showing;

    private int screenW;
    private int screenH;

    public PanelWindow(Context context, WindowManager windowManager, Listener listener) {
        this.context = context;
        this.windowManager = windowManager;
        this.listener = listener;
    }

    public boolean isShowing() {
        return showing;
    }

    private int dp(float v) {
        return Util.dp(context, v);
    }

    private int color(int res) {
        return context.getColor(res);
    }

    public void show() {
        if (showing) {
            return;
        }
        int[] screen = Util.screen(context);
        screenW = screen[0];
        screenH = screen[1];

        int panelW = Math.min(screenW - dp(40), dp(430));
        int panelH = Math.min((int) (screenH * 0.64f), dp(680));

        root = new PanelView(context);
        root.setBackgroundColor(0x4D000000);
        root.setFocusableInTouchMode(true);

        panel = new FrameLayout(context);
        panel.setBackgroundResource(R.drawable.bg_panel);
        panel.setClickable(true);
        panel.setElevation(dp(18));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(10));
        panel.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- header: search + close
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        LinearLayout searchBox = new LinearLayout(context);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackgroundResource(R.drawable.bg_search);
        searchBox.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        header.addView(searchBox, searchLp);

        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchBox.addView(searchIcon, new LinearLayout.LayoutParams(dp(17), dp(17)));

        search = new EditText(context);
        search.setBackground(null);
        search.setHint(R.string.search_hint);
        search.setHintTextColor(color(R.color.muted));
        search.setTextColor(color(R.color.ink));
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setPadding(dp(9), 0, 0, 0);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        searchBox.addView(search, new LinearLayout.LayoutParams(0, dp(42), 1f));

        FrameLayout close = new FrameLayout(context);
        close.setBackgroundResource(R.drawable.bg_circle_soft);
        close.setClickable(true);
        ImageView closeIcon = new ImageView(context);
        closeIcon.setImageResource(R.drawable.ic_close);
        close.addView(closeIcon, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        closeLp.leftMargin = dp(10);
        header.addView(close, closeLp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });

        // ---- grid
        adapter = new AppAdapter(context);
        adapter.setApps(AppRepo.get(context));

        GridView grid = new GridView(context);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(4));
        grid.setHorizontalSpacing(dp(2));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(10), 0, dp(6));
        grid.setAdapter(adapter);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        content.addView(grid, gridLp);

        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                AppRepo.App app = adapter.getItem(position);
                if (app == null) {
                    return;
                }
                hide();
                listener.onPick(app);
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s == null ? "" : s.toString());
            }
        });

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(panelW, panelH);
        panelLp.gravity = Gravity.CENTER;
        root.addView(panel, panelLp);

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        panel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // swallow
            }
        });

        params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        windowManager.addView(root, params);
        root.requestFocus();
        showing = true;

        root.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        panel.setAlpha(0f);
        root.animate().alpha(1f).setDuration(140).start();
        panel.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(210)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    public void hide() {
        if (!showing || root == null) {
            return;
        }
        showing = false;
        hideKeyboard();
        final View dying = root;
        root = null;
        panel.animate().scaleX(0.96f).scaleY(0.96f).alpha(0f)
                .setDuration(140)
                .start();
        dying.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
            @Override
            public void run() {
                removeNow(dying);
            }
        }).start();
    }

    public void hideImmediate() {
        if (root == null) {
            return;
        }
        showing = false;
        View dying = root;
        root = null;
        removeNow(dying);
    }

    private void removeNow(View view) {
        try {
            windowManager.removeView(view);
        } catch (Throwable ignored) {
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                if (search != null) {
                    search.clearFocus();
                }
            }
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && root != null) {
                imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private class PanelView extends FrameLayout {
        PanelView(Context c) {
            super(c);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    hide();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
    }

    // ------------------------------------------------------------ adapter

    private static final class Holder {
        ImageView icon;
        TextView label;
    }

    private final class AppAdapter extends BaseAdapter {

        private final Context ctx;
        private final List<AppRepo.App> all = new ArrayList<>();
        private final List<AppRepo.App> visible = new ArrayList<>();

        AppAdapter(Context ctx) {
            this.ctx = ctx;
        }

        void setApps(List<AppRepo.App> apps) {
            all.clear();
            all.addAll(apps);
            visible.clear();
            visible.addAll(apps);
            notifyDataSetChanged();
        }

        void filter(String query) {
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            if (q.isEmpty()) {
                visible.addAll(all);
            } else {
                for (AppRepo.App app : all) {
                    if (app.label.toLowerCase(Locale.ROOT).contains(q)
                            || app.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                        visible.add(app);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public AppRepo.App getItem(int position) {
            return position >= 0 && position < visible.size() ? visible.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder holder;
            LinearLayout item;
            if (convertView == null) {
                item = new LinearLayout(ctx);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                item.setBackgroundResource(R.drawable.sel_grid_item);
                item.setPadding(dp(4), dp(10), dp(4), dp(8));

                holder = new Holder();
                holder.icon = new ImageView(ctx);
                item.addView(holder.icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

                holder.label = new TextView(ctx);
                holder.label.setTextSize(11f);
                holder.label.setGravity(Gravity.CENTER);
                holder.label.setMaxLines(1);
                holder.label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                holder.label.setTextColor(color(R.color.ink));
                holder.label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                labelLp.topMargin = dp(7);
                item.addView(holder.label, labelLp);
                item.setTag(holder);
            } else {
                item = (LinearLayout) convertView;
                holder = (Holder) item.getTag();
            }

            AppRepo.App app = getItem(position);
            if (app != null) {
                if (app.icon != null) {
                    holder.icon.setImageDrawable(app.icon);
                } else {
                    holder.icon.setImageResource(R.drawable.ic_window);
                }
                holder.label.setText(app.label);
            }
            return item;
        }
    }
}
