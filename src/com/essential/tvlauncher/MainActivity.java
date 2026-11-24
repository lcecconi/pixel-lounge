package com.essential.tvlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int DRAWER_COLUMNS = 3;

    private PrefsManager prefs;
    private FrameLayout rootLayout;
    private FrameLayout homeLayer;
    private RetroSunsetView sunsetView;
    private SoftHaloView trayHaloView;
    private TextView dateText;
    private TextClock clockView;
    private LinearLayout centerStage;
    private LinearLayout favoritesRow;
    private TextView focusedAppCaption;

    private LinearLayout resumeSection;
    private TextView resumeHeader;
    private LinearLayout resumeRow;

    private FrameLayout sideOverlayScrim;
    private View lastFocusedBeforeOverlay;

    private List<AppInfo> allInstalledApps = new ArrayList<>();
    private Map<String, AppInfo> appByPackage = new HashMap<>();

    private Typeface pixelFont;
    private Typeface pixelTitleFont;
    private Runnable drawerBackAction = null;

    private int getFontDeltaSp() {
        int mode = prefs.getFontSizeMode();
        if (mode == 1) return 2;   // Large
        if (mode == 2) return -2;  // Small
        return 0;                  // Regular
    }

    private int getTitleFontDeltaSp() {
        int mode = prefs.getFontSizeMode();
        if (mode == 1) return 3;   // Large
        if (mode == 2) return -3;  // Small
        return 0;                  // Regular
    }

    private void applyFontSize() {
        if (dateText != null) {
            dateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 + getFontDeltaSp());
        }
        if (focusedAppCaption != null) {
            focusedAppCaption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11 + getFontDeltaSp());
        }
        if (resumeHeader != null) {
            resumeHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9 + getTitleFontDeltaSp());
        }
        if (clockView != null) {
            clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17 + getTitleFontDeltaSp());
        }
        renderFavorites();
        renderResumeBar();
    }

    // Interactive Reorder Mode state
    private String reorderingPackage = null;
    private String pendingFocusPackage = null;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadAppsAndRender();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new PrefsManager(this);

        try {
            pixelFont = getResources().getFont(R.font.silkscreen);
            pixelTitleFont = getResources().getFont(R.font.press_start_2p);
        } catch (Exception e) {
            pixelFont = Typeface.MONOSPACE;
            pixelTitleFont = Typeface.MONOSPACE;
        }

        buildRootUi();
        applyTheme();
        reloadAppsAndRender();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDateText();
        renderResumeBar();
        handleThemeIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleThemeIntent(intent);
    }

    private void handleThemeIntent(Intent intent) {
        if (intent == null) return;
        int themeIdx = intent.getIntExtra("theme", intent.getIntExtra("capture_theme", -1));
        if (themeIdx >= 0 && themeIdx < ThemeCatalog.THEMES.length) {
            prefs.setThemeIndex(themeIdx);
            applyTheme();
            renderFavorites();
            renderResumeBar();
        }
        if (intent.hasExtra("open_drawer")) {
            String drawer = intent.getStringExtra("open_drawer");
            intent.removeExtra("open_drawer");
            if ("customize".equals(drawer)) {
                openCustomizeSideDrawer();
            } else if ("themes".equals(drawer)) {
                openThemesSideDrawer();
            } else if ("all_apps".equals(drawer)) {
                openAllAppsSideDrawer();
            }
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (reorderingPackage != null) {
            exitReorderMode();
            return;
        }
        if (sideOverlayScrim.getVisibility() == View.VISIBLE) {
            if (drawerBackAction != null) {
                Runnable act = drawerBackAction;
                drawerBackAction = null;
                act.run();
                return;
            }
            closeSideDrawer();
            return;
        }
        // Home launcher does not exit on Back
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Handle live continuous Left/Right reordering when Reorder Mode is active
        if (reorderingPackage != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (prefs.moveFavorite(reorderingPackage, -1)) {
                    pendingFocusPackage = reorderingPackage;
                    renderFavorites();
                }
                return true;
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (prefs.moveFavorite(reorderingPackage, +1)) {
                    pendingFocusPackage = reorderingPackage;
                    renderFavorites();
                }
                return true;
            } else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_BACK) {
                exitReorderMode();
                return true;
            } else if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Keep locked on the row while reordering
                return true;
            }
        }

        // Prevent D-pad Left/Right from ever leaking out of single-column side panels
        if (sideOverlayScrim.getVisibility() == View.VISIBLE && event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK) {
                if (drawerBackAction != null) {
                    Runnable act = drawerBackAction;
                    drawerBackAction = null;
                    act.run();
                } else {
                    closeSideDrawer();
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && "single_col".equals(sideOverlayScrim.getTag())) {
                if (drawerBackAction != null) {
                    Runnable act = drawerBackAction;
                    drawerBackAction = null;
                    act.run();
                } else {
                    closeSideDrawer();
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && "single_col".equals(sideOverlayScrim.getTag())) {
                return true; // Absorb right overflow
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void buildRootUi() {
        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootLayout.setClipChildren(false);
        rootLayout.setClipToPadding(false);

        // 1. Full-screen 8-bit Digital Sunset pixel-art background
        sunsetView = new RetroSunsetView(this);
        sunsetView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootLayout.addView(sunsetView);

        // 1b. Soft organic radial halo layer behind the two right-justified trays (zero hard edges!)
        trayHaloView = new SoftHaloView(this);
        trayHaloView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootLayout.addView(trayHaloView);

        // 2. Home Content Layer (isolated so we can block its focus whenever side drawer is open!)
        homeLayer = new FrameLayout(this);
        homeLayer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        homeLayer.setClipChildren(false);
        homeLayer.setClipToPadding(false);

        // 2a. Minimal Top Bar floating over starry upper sky
        View topBar = buildTopBar();
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        topBar.setLayoutParams(topLp);
        homeLayer.addView(topBar);

        // 2b. Curated Favorites Row — floating in the MIDDLE, RIGHT-JUSTIFIED with faded halo behind tray
        centerStage = new LinearLayout(this);
        centerStage.setOrientation(LinearLayout.VERTICAL);
        centerStage.setGravity(Gravity.END);
        centerStage.setClipChildren(false);
        centerStage.setClipToPadding(false);
        centerStage.setPadding(0, dp(8), 0, dp(10));
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
        );
        centerLp.topMargin = dp(-12);
        centerStage.setLayoutParams(centerLp);

        HorizontalScrollView favScroll = new HorizontalScrollView(this);
        favScroll.setHorizontalScrollBarEnabled(false);
        favScroll.setClipChildren(false);
        favScroll.setClipToPadding(false);
        favScroll.setFillViewport(true);
        favScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        favoritesRow = new LinearLayout(this);
        favoritesRow.setOrientation(LinearLayout.HORIZONTAL);
        favoritesRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        favoritesRow.setClipChildren(false);
        favoritesRow.setClipToPadding(false);
        favoritesRow.setPadding(dp(44), dp(12), dp(44), dp(10));
        favScroll.addView(favoritesRow);
        centerStage.addView(favScroll);

        // Minimal focused app caption right-aligned beneath the favorites bar
        focusedAppCaption = new TextView(this);
        focusedAppCaption.setTextColor(0xFFFFFFFF);
        focusedAppCaption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11 + getFontDeltaSp());
        focusedAppCaption.setTypeface(pixelFont);
        focusedAppCaption.setLetterSpacing(0.08f);
        focusedAppCaption.setShadowLayer(4f, 0f, 2f, 0xCC000000);
        focusedAppCaption.setGravity(Gravity.END);
        focusedAppCaption.setPadding(dp(44), 0, dp(44), 0);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        capLp.topMargin = dp(2);
        focusedAppCaption.setLayoutParams(capLp);
        centerStage.addView(focusedAppCaption);

        homeLayer.addView(centerStage);

        // 2c. Resume Watching Bar (top 3 most recent) docked cleanly at bottom-right with faded halo
        resumeSection = new LinearLayout(this);
        resumeSection.setOrientation(LinearLayout.VERTICAL);
        resumeSection.setGravity(Gravity.END);
        resumeSection.setClipChildren(false);
        resumeSection.setClipToPadding(false);
        resumeSection.setPadding(0, dp(10), 0, dp(8));
        FrameLayout.LayoutParams resLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        resLp.bottomMargin = dp(12);
        resumeSection.setLayoutParams(resLp);

        resumeHeader = new TextView(this);
        resumeHeader.setText("RESUME WATCHING");
        resumeHeader.setTextColor(0xA6FFF5E1);
        resumeHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9 + getTitleFontDeltaSp());
        resumeHeader.setTypeface(pixelTitleFont);
        resumeHeader.setLetterSpacing(0.12f);
        resumeHeader.setShadowLayer(3f, 0f, 1f, 0xCC000000);
        resumeHeader.setGravity(Gravity.END);
        resumeHeader.setPadding(dp(44), 0, dp(44), dp(4));
        resumeSection.addView(resumeHeader);

        HorizontalScrollView resScroll = new HorizontalScrollView(this);
        resScroll.setHorizontalScrollBarEnabled(false);
        resScroll.setClipChildren(false);
        resScroll.setClipToPadding(false);
        resScroll.setFillViewport(true);
        resScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        resumeRow = new LinearLayout(this);
        resumeRow.setOrientation(LinearLayout.HORIZONTAL);
        resumeRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        resumeRow.setClipChildren(false);
        resumeRow.setClipToPadding(false);
        resumeRow.setPadding(dp(44), dp(6), dp(44), dp(10));
        resScroll.addView(resumeRow);
        resumeSection.addView(resScroll);

        homeLayer.addView(resumeSection);
        rootLayout.addView(homeLayer);

        // 3. Side Drawer Overlay Scrim (slides menus in from the right edge)
        sideOverlayScrim = new FrameLayout(this);
        sideOverlayScrim.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        sideOverlayScrim.setBackgroundColor(0x6606040E); // Subtle scrim so sunset stays visible on the left!
        sideOverlayScrim.setVisibility(View.GONE);
        sideOverlayScrim.setClickable(true);
        sideOverlayScrim.setFocusable(false);
        sideOverlayScrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSideDrawer();
            }
        });
        rootLayout.addView(sideOverlayScrim);

        setContentView(rootLayout);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        bar.setPadding(dp(40), dp(22), dp(40), dp(12));

        // Left: Minimal monospace date
        dateText = new TextView(this);
        dateText.setTextColor(0xD8FFF5E1);
        dateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 + getFontDeltaSp());
        dateText.setTypeface(pixelFont);
        dateText.setLetterSpacing(0.08f);
        dateText.setShadowLayer(4f, 0f, 2f, 0xCC000000);
        updateDateText();

        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        dateText.setLayoutParams(dateLp);
        bar.addView(dateText);

        // Right: All Apps Side Drawer button
        View appsBtn = createTopIconButton(R.drawable.ic_apps, "All Apps", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAllAppsSideDrawer();
            }
        });
        LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        abLp.rightMargin = dp(10);
        appsBtn.setLayoutParams(abLp);
        bar.addView(appsBtn);

        // Right: Theme / Customize Side Drawer button
        View themeBtn = createTopIconButton(R.drawable.ic_palette, "Customize", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCustomizeSideDrawer();
            }
        });
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        tbLp.rightMargin = dp(10);
        themeBtn.setLayoutParams(tbLp);
        bar.addView(themeBtn);

        // Right: Android System Settings button
        View settingsBtn = createTopIconButton(R.drawable.ic_settings, "Settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Cannot open Settings", Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        sbLp.rightMargin = dp(18);
        settingsBtn.setLayoutParams(sbLp);
        bar.addView(settingsBtn);

        // Right: Crisp Monospace Digital Clock
        clockView = new TextClock(this);
        clockView.setFormat24Hour("HH:mm");
        clockView.setFormat12Hour("h:mm");
        clockView.setTextColor(0xFFFFF8E7);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17 + getTitleFontDeltaSp());
        clockView.setTypeface(pixelTitleFont);
        clockView.setLetterSpacing(0.06f);
        clockView.setShadowLayer(6f, 0f, 2f, 0xCC000000);
        bar.addView(clockView);

        return bar;
    }

    private View createTopIconButton(int drawableRes, final String label, View.OnClickListener listener) {
        final FrameLayout btn = new FrameLayout(this);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setContentDescription(label);

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setCornerRadius(dp(8));
        normalBg.setColor(0x55120A24);
        normalBg.setStroke(dp(1), 0x33FFFFFF);

        final GradientDrawable focusedBg = new GradientDrawable();
        focusedBg.setCornerRadius(dp(8));
        focusedBg.setColor(0xCC1E1138);
        focusedBg.setStroke(dp(2), ThemeCatalog.get(prefs.getThemeIndex()).accentColor);

        btn.setBackground(normalBg);

        ImageView iv = new ImageView(this);
        iv.setImageResource(drawableRes);
        iv.setColorFilter(0xFFFFF5E1);
        iv.setLayoutParams(new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.CENTER));
        btn.addView(iv);

        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                ThemeCatalog.ThemeSpec t = ThemeCatalog.get(prefs.getThemeIndex());
                focusedBg.setColor(withAlpha(t.mountainFar, 0xDD));
                focusedBg.setStroke(dp(2), t.accentColor);
                v.setBackground(hasFocus ? focusedBg : normalBg);
                animateScale(v, hasFocus ? 1.15f : 1.0f, hasFocus ? dp(8) : 0f);
                if (hasFocus && reorderingPackage == null) {
                    focusedAppCaption.setText(label.toUpperCase(Locale.US));
                }
            }
        });
        btn.setOnClickListener(listener);
        return btn;
    }

    private void updateDateText() {
        if (dateText != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE · MMM d", Locale.getDefault());
            dateText.setText(sdf.format(new Date()).toUpperCase(Locale.getDefault()));
        }
    }

    private void applyTheme() {
        int idx = prefs.getThemeIndex();
        sunsetView.setTheme(idx);
        sunsetView.setAnimateEnabled(prefs.isAnimateBg());
        sunsetView.setScanlinesEnabled(prefs.isScanlines());

        // True elliptical radial halos that fade smoothly to 0% transparency in ALL directions
        ThemeCatalog.ThemeSpec theme = ThemeCatalog.get(idx);
        trayHaloView.setHaloColor(theme.waterBase);
        trayHaloView.setShowBottomHalo(prefs.isShowResumeBar());
        centerStage.setBackground(null);
        resumeSection.setBackground(null);
    }

    private void reloadAppsAndRender() {
        allInstalledApps = AppLoader.loadInstalledApps(this);
        appByPackage.clear();
        for (AppInfo app : allInstalledApps) {
            appByPackage.put(app.packageName, app);
        }
        AppLoader.ensureInitialFavorites(prefs, allInstalledApps);
        renderFavorites();
        renderResumeBar();
    }

    private void renderFavorites() {
        favoritesRow.removeAllViews();

        Set<String> hidden = prefs.getHiddenApps();
        List<String> favPackages = prefs.getFavorites();

        List<AppInfo> favApps = new ArrayList<>();
        for (String pkg : favPackages) {
            if (!hidden.contains(pkg) && appByPackage.containsKey(pkg)) {
                favApps.add(appByPackage.get(pkg));
            }
        }

        // Configurable icon sizes (Default: 118x66dp, Compact: 100x56dp, Large: 134x76dp)
        int sizeMode = prefs.getIconSizeMode();
        int cardW = dp(sizeMode == 1 ? 100 : (sizeMode == 2 ? 134 : 118));
        int cardH = dp(sizeMode == 1 ? 56 : (sizeMode == 2 ? 76 : 66));

        View viewToFocus = null;

        if (favApps.isEmpty()) {
            View addBtn = createSideMenuButton("+ ADD FAVORITE APPS", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openAllAppsSideDrawer();
                }
            });
            addBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT));
            favoritesRow.addView(addBtn);
            viewToFocus = addBtn;
            focusedAppCaption.setText("");
        } else {
            for (int i = 0; i < favApps.size(); i++) {
                AppInfo app = favApps.get(i);
                boolean isBeingReordered = app.packageName.equals(reorderingPackage);
                View card = createCompactAppCard(app, cardW, cardH, true, isBeingReordered);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                if (i < favApps.size() - 1) {
                    lp.rightMargin = dp(12);
                }
                card.setLayoutParams(lp);
                favoritesRow.addView(card);

                if (viewToFocus == null && pendingFocusPackage == null) {
                    viewToFocus = card;
                }
                if (app.packageName.equals(pendingFocusPackage)) {
                    viewToFocus = card;
                }
            }
        }

        pendingFocusPackage = null;

        if (reorderingPackage != null) {
            focusedAppCaption.setText("◀   MOVE   ▶   ·   OK TO SAVE");
            focusedAppCaption.setTextColor(ThemeCatalog.get(prefs.getThemeIndex()).accentColor);
        } else {
            focusedAppCaption.setTextColor(0xF0FFF8E7);
        }

        if (viewToFocus != null && sideOverlayScrim.getVisibility() != View.VISIBLE) {
            final View target = viewToFocus;
            target.post(new Runnable() {
                @Override
                public void run() {
                    target.requestFocus();
                }
            });
        }
    }

    private void renderResumeBar() {
        resumeRow.removeAllViews();

        if (!prefs.isShowResumeBar()) {
            resumeSection.setVisibility(View.GONE);
            return;
        }

        // 1. Try native Android TV Watch Next programs first (top 3 most recent only!)
        List<WatchNextLoader.WatchNextItem> watchNext = WatchNextLoader.loadWatchNext(this);
        if (!watchNext.isEmpty()) {
            resumeSection.setVisibility(View.VISIBLE);
            resumeHeader.setText("RESUME WATCHING");

            int cardW = dp(156);
            int cardH = dp(88);
            int count = Math.min(3, watchNext.size());
            for (int i = 0; i < count; i++) {
                View card = createWatchNextCard(watchNext.get(i), cardW, cardH);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
                if (i < count - 1) lp.rightMargin = dp(14);
                card.setLayoutParams(lp);
                resumeRow.addView(card);
            }
            return;
        }

        // 2. Fallback: Recently opened apps so the Resume bar is immediately useful even before
        //    an app writes a Watch Next entry!
        List<String> recentPkgs = prefs.getRecentApps();
        Set<String> hidden = prefs.getHiddenApps();
        List<AppInfo> recentApps = new ArrayList<>();
        for (String pkg : recentPkgs) {
            if (!hidden.contains(pkg) && appByPackage.containsKey(pkg)) {
                recentApps.add(appByPackage.get(pkg));
            }
        }

        if (recentApps.isEmpty()) {
            resumeSection.setVisibility(View.GONE);
            return;
        }

        resumeSection.setVisibility(View.VISIBLE);
        resumeHeader.setText("RECENTLY OPENED");
        int pillH = dp(34);
        int recentCount = Math.min(3, recentApps.size());
        for (int i = 0; i < recentCount; i++) {
            View pill = createRecentPill(recentApps.get(i), pillH);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    pillH
            );
            if (i < recentCount - 1) lp.rightMargin = dp(10);
            pill.setLayoutParams(lp);
            resumeRow.addView(pill);
        }
    }

    private View createWatchNextCard(final WatchNextLoader.WatchNextItem item, int widthPx, int heightPx) {
        final ThemeCatalog.ThemeSpec theme = ThemeCatalog.get(prefs.getThemeIndex());
        final int radiusPx = dp(8);

        final FrameLayout card = new FrameLayout(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(widthPx, heightPx));
        card.setFocusable(true);
        card.setClickable(true);
        card.setAlpha(1.0f);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(radiusPx);
        bg.setColor(withAlpha(theme.mountainNear, 0xFF));
        card.setBackground(bg);

        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        card.setClipToOutline(true);

        // Poster artwork
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        if (item.posterUri != null && !item.posterUri.isEmpty()) {
            WatchNextLoader.loadPosterAsync(this, item.posterUri, poster);
        } else if (appByPackage.containsKey(item.packageName)) {
            AppInfo srcApp = appByPackage.get(item.packageName);
            if (srcApp.banner != null) poster.setImageDrawable(srcApp.banner);
        }
        card.addView(poster);

        // Bottom dark scrim for title legibility
        View scrim = new View(this);
        GradientDrawable scrimBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0x00000000, 0xCC090614 }
        );
        scrim.setBackground(scrimBg);
        scrim.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36), Gravity.BOTTOM
        ));
        card.addView(scrim);

        // Title inside card bottom
        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextColor(0xFFF8FAFC);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9 + getFontDeltaSp());
        title.setTypeface(pixelFont);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        );
        tlp.leftMargin = dp(8);
        tlp.rightMargin = dp(8);
        tlp.bottomMargin = dp(6);
        title.setLayoutParams(tlp);
        card.addView(title);

        // Playback progress bar along very bottom edge
        if (item.progress > 0.01f) {
            View track = new View(this);
            track.setBackgroundColor(0x55FFFFFF);
            track.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.BOTTOM
            ));
            card.addView(track);

            View fill = new View(this);
            fill.setBackgroundColor(theme.accentColor);
            fill.setLayoutParams(new FrameLayout.LayoutParams(
                    Math.max(dp(6), (int) (widthPx * item.progress)), dp(3), Gravity.BOTTOM | Gravity.START
            ));
            card.addView(fill);
        }

        // Focus border
        final View border = new View(this);
        border.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        final GradientDrawable bd = new GradientDrawable();
        bd.setCornerRadius(radiusPx);
        bd.setColor(Color.TRANSPARENT);
        bd.setStroke(dp(3), theme.accentColor);
        border.setBackground(bd);
        border.setAlpha(0f);
        card.addView(border);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bd.setStroke(dp(3), ThemeCatalog.get(prefs.getThemeIndex()).accentColor);
                animateScale(v, hasFocus ? 1.10f : 1.0f, hasFocus ? dp(12) : 0f);
                border.animate().alpha(hasFocus ? 1f : 0f).setDuration(110).start();
                if (hasFocus) {
                    String cap = item.title;
                    if (item.subtitle != null && !item.subtitle.isEmpty()) {
                        cap += " · " + item.subtitle;
                    }
                    focusedAppCaption.setText(cap.toUpperCase(Locale.getDefault()));
                }
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (item.intentUri != null && !item.intentUri.isEmpty()) {
                        Intent intent = Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return;
                    }
                } catch (Exception ignored) {}
                if (appByPackage.containsKey(item.packageName)) {
                    launchApp(appByPackage.get(item.packageName));
                }
            }
        });

        return card;
    }

    private View createRecentPill(final AppInfo app, int heightPx) {
        final ThemeCatalog.ThemeSpec theme = ThemeCatalog.get(prefs.getThemeIndex());
        final LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10), 0, dp(14), 0);
        pill.setFocusable(true);
        pill.setClickable(true);
        pill.setAlpha(0.84f);

        final GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(heightPx / 2f);
        normal.setColor(withAlpha(theme.mountainNear, 0xBB));
        normal.setStroke(dp(1), 0x33FFFFFF);

        final GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(heightPx / 2f);
        focused.setColor(withAlpha(theme.mountainFar, 0xEE));
        focused.setStroke(dp(2), theme.accentColor);

        pill.setBackground(normal);

        if (app.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(18), dp(18));
            ilp.rightMargin = dp(8);
            iv.setLayoutParams(ilp);
            pill.addView(iv);
        }

        TextView tv = new TextView(this);
        tv.setText(app.label);
        tv.setTextColor(0xFFF1F5F9);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pill.addView(tv);

        pill.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                ThemeCatalog.ThemeSpec t = ThemeCatalog.get(prefs.getThemeIndex());
                focused.setColor(withAlpha(t.mountainFar, 0xEE));
                focused.setStroke(dp(2), t.accentColor);
                v.setBackground(hasFocus ? focused : normal);
                animateScale(v, hasFocus ? 1.10f : 1.0f, hasFocus ? dp(8) : 0f);
                v.animate().alpha(hasFocus ? 1.0f : 0.84f).setDuration(110).start();
                if (hasFocus) {
                    focusedAppCaption.setText("RESUME " + app.label.toUpperCase(Locale.getDefault()));
                }
            }
        });

        pill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(app);
            }
        });

        return pill;
    }

    private View createCompactAppCard(final AppInfo app, int widthPx, int heightPx,
                                      final boolean isHomeDock, final boolean isReordering) {
        final ThemeCatalog.ThemeSpec theme = ThemeCatalog.get(prefs.getThemeIndex());
        final LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setFocusable(true);
        wrapper.setClickable(true);
        wrapper.setLongClickable(true);
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);

        final int radiusPx = dp(8);

        final FrameLayout cardBox = new FrameLayout(this);
        cardBox.setLayoutParams(new LinearLayout.LayoutParams(widthPx, heightPx));

        final GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(radiusPx);
        cardBg.setColor(withAlpha(theme.mountainNear, 0xFF));
        cardBox.setBackground(cardBg);

        cardBox.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        cardBox.setClipToOutline(true);

        if (app.banner != null) {
            ImageView bannerView = new ImageView(this);
            bannerView.setImageDrawable(app.banner);
            bannerView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bannerView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            cardBox.addView(bannerView);
        } else if (app.icon != null) {
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(app.icon);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconSize = Math.min(dp(38), (int) (heightPx * 0.58f));
            iconView.setLayoutParams(new FrameLayout.LayoutParams(
                    iconSize, iconSize, Gravity.CENTER
            ));
            cardBox.addView(iconView);
        }

        // Subtle resting rim
        View restingBorder = new View(this);
        restingBorder.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        GradientDrawable rim = new GradientDrawable();
        rim.setCornerRadius(radiusPx);
        rim.setColor(Color.TRANSPARENT);
        rim.setStroke(dp(1), 0x44FFFFFF);
        restingBorder.setBackground(rim);
        cardBox.addView(restingBorder);

        // Bright pixel-accent focus border
        final View borderOverlay = new View(this);
        borderOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        final GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setCornerRadius(radiusPx);
        borderDrawable.setColor(Color.TRANSPARENT);
        borderDrawable.setStroke(dp(isReordering ? 4 : 3),
                isReordering ? 0xFFFFFFFF : theme.accentColor);
        borderOverlay.setBackground(borderDrawable);
        borderOverlay.setAlpha(isReordering ? 1f : 0f);
        cardBox.addView(borderOverlay);

        wrapper.addView(cardBox);

        final TextView drawerTitle;
        if (!isHomeDock) {
            drawerTitle = new TextView(this);
            drawerTitle.setText(app.label);
            drawerTitle.setTextColor(0xB3E2E8F0);
            drawerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9 + getFontDeltaSp());
            drawerTitle.setTypeface(pixelFont);
            drawerTitle.setSingleLine(true);
            drawerTitle.setEllipsize(TextUtils.TruncateAt.END);
            drawerTitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    widthPx, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            tvLp.topMargin = dp(4);
            drawerTitle.setLayoutParams(tvLp);
            wrapper.addView(drawerTitle);
        } else {
            drawerTitle = null;
        }

        wrapper.setAlpha(1.0f);
        if (isReordering) {
            wrapper.setScaleX(1.16f);
            wrapper.setScaleY(1.16f);
            wrapper.setElevation(dp(16));
        }

        wrapper.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                ThemeCatalog.ThemeSpec t = ThemeCatalog.get(prefs.getThemeIndex());
                boolean reorderingThis = app.packageName.equals(reorderingPackage);
                borderDrawable.setStroke(dp(reorderingThis ? 4 : 3),
                        reorderingThis ? 0xFFFFFFFF : t.accentColor);
                float targetScale = reorderingThis ? 1.16f : (hasFocus ? 1.12f : 1.0f);
                animateScale(v, targetScale, hasFocus ? dp(14) : 0f);
                borderOverlay.animate().alpha((hasFocus || reorderingThis) ? 1f : 0f).setDuration(110).start();
                if (isHomeDock) {
                    if (hasFocus && reorderingPackage == null) {
                        focusedAppCaption.setText(prefs.isShowLabels()
                                ? app.label.toUpperCase(Locale.getDefault())
                                : "");
                    }
                } else if (drawerTitle != null) {
                    drawerTitle.setTextColor(hasFocus ? 0xFFFFFFFF : 0xB3E2E8F0);
                }
            }
        });

        wrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (reorderingPackage != null) {
                    exitReorderMode();
                    return;
                }
                if (!isHomeDock) {
                    closeSideDrawer();
                }
                launchApp(app);
            }
        });

        wrapper.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (reorderingPackage != null) return true;
                openAppOptionsSideDrawer(app, isHomeDock);
                return true;
            }
        });

        wrapper.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (reorderingPackage != null) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                    openAppOptionsSideDrawer(app, isHomeDock);
                    return true;
                }
                return false;
            }
        });

        return wrapper;
    }

    private void enterReorderMode(String packageName) {
        reorderingPackage = packageName;
        pendingFocusPackage = packageName;
        closeSideDrawer();
        renderFavorites();
    }

    private void exitReorderMode() {
        String pkg = reorderingPackage;
        reorderingPackage = null;
        pendingFocusPackage = pkg;
        renderFavorites();
    }

    private void launchApp(AppInfo app) {
        prefs.recordRecentApp(app.packageName);
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(app.isLeanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(app.packageName, app.activityName));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (Exception e) {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (fallback != null) {
                startActivity(fallback);
            } else {
                Toast.makeText(this, "Could not launch " + app.label, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== THEME-INTEGRATED RIGHT SIDE DRAWERS ====================

    private void showSideDrawer(final LinearLayout drawer, int widthDp, boolean isSingleColumn) {
        boolean alreadyOpen = sideOverlayScrim.getVisibility() == View.VISIBLE;
        if (!alreadyOpen) {
            lastFocusedBeforeOverlay = getCurrentFocus();
        }

        // CRITICAL: Block all focus on background home layer so D-pad arrows NEVER leak!
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        sideOverlayScrim.removeAllViews();
        sideOverlayScrim.setTag(isSingleColumn ? "single_col" : "grid_col");

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(widthDp),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
        );
        drawer.setLayoutParams(lp);
        sideOverlayScrim.addView(drawer);
        sideOverlayScrim.setVisibility(View.VISIBLE);

        if (!alreadyOpen) {
            drawer.setTranslationX(dp(widthDp));
            drawer.animate()
                    .translationX(0f)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        drawer.post(new Runnable() {
            @Override
            public void run() {
                View first = findFirstFocusable(drawer);
                if (first != null) first.requestFocus();
            }
        });
    }

    private void closeSideDrawer() {
        drawerBackAction = null;
        sideOverlayScrim.setVisibility(View.GONE);
        sideOverlayScrim.removeAllViews();
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        if (lastFocusedBeforeOverlay != null) {
            final View prev = lastFocusedBeforeOverlay;
            lastFocusedBeforeOverlay = null;
            prev.post(new Runnable() {
                @Override
                public void run() {
                    if (prev.isAttachedToWindow()) {
                        prev.requestFocus();
                    }
                }
            });
        }
    }

    private LinearLayout createSideDrawerContainer(String title, String subtitle) {
        ThemeCatalog.ThemeSpec theme = ThemeCatalog.get(prefs.getThemeIndex());

        LinearLayout outerWrapper = new LinearLayout(this);
        outerWrapper.setOrientation(LinearLayout.HORIZONTAL);
        outerWrapper.setClickable(true);
        outerWrapper.setFocusable(false);

        // Authentic 8-bit double frame on the left edge
        LinearLayout leftBorderFrame = new LinearLayout(this);
        leftBorderFrame.setOrientation(LinearLayout.HORIZONTAL);
        leftBorderFrame.setLayoutParams(new LinearLayout.LayoutParams(
                dp(6), ViewGroup.LayoutParams.MATCH_PARENT
        ));

        View outerBar = new View(this);
        outerBar.setBackgroundColor(theme.accentColor);
        outerBar.setLayoutParams(new LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT));
        leftBorderFrame.addView(outerBar);

        View gap = new View(this);
        gap.setBackgroundColor(0xFF000000);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT));
        leftBorderFrame.addView(gap);

        View innerBar = new View(this);
        innerBar.setBackgroundColor(withAlpha(theme.accentColor, 0x88));
        innerBar.setLayoutParams(new LinearLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT));
        leftBorderFrame.addView(innerBar);

        outerWrapper.addView(leftBorderFrame);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(22), dp(28), dp(22), dp(20));
        inner.setClipChildren(false);
        inner.setClipToPadding(false);

        // Solid retro canvas matching the theme's waterBase palette
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(0);
        bg.setColor(withAlpha(theme.waterBase, 0xF6));
        bg.setStroke(dp(1), withAlpha(theme.mountainFar, 0x99));
        inner.setBackground(bg);
        inner.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
        ));

        // 8-bit Title
        TextView titleTv = new TextView(this);
        titleTv.setText(title.toUpperCase(Locale.getDefault()));
        titleTv.setTextColor(theme.accentColor);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12 + getTitleFontDeltaSp());
        titleTv.setTypeface(pixelTitleFont);
        titleTv.setLetterSpacing(0.06f);
        titleTv.setShadowLayer(3f, 0f, 1f, 0xFF000000);
        inner.addView(titleTv);

        // Retro pixel accent divider bar
        View div = new View(this);
        div.setBackgroundColor(withAlpha(theme.accentColor, 0x77));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)
        );
        dlp.topMargin = dp(8);
        dlp.bottomMargin = dp(6);
        div.setLayoutParams(dlp);
        inner.addView(div);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(this);
            subTv.setText(subtitle);
            subTv.setTextColor(0xFFCBD5E1);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9 + getFontDeltaSp());
            subTv.setTypeface(pixelFont);
            subTv.setLetterSpacing(0.04f);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            slp.bottomMargin = dp(14);
            subTv.setLayoutParams(slp);
            inner.addView(subTv);
        } else {
            View sp = new View(this);
            sp.setLayoutParams(new LinearLayout.LayoutParams(1, dp(12)));
            inner.addView(sp);
        }

        outerWrapper.addView(inner);
        outerWrapper.setTag(inner);
        return outerWrapper;
    }

    private LinearLayout getDrawerInner(LinearLayout outerWrapper) {
        return (LinearLayout) outerWrapper.getTag();
    }

    private View createSideMenuButton(final String text, final boolean isDestructive, View.OnClickListener listener) {
        final ThemeCatalog.ThemeSpec theme = ThemeCatalog.get(prefs.getThemeIndex());
        final TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 + getFontDeltaSp());
        btn.setTypeface(pixelFont);
        btn.setTextColor(isDestructive ? 0xFFFCA5A5 : 0xFFE2E8F0);
        btn.setPadding(dp(14), dp(10), dp(14), dp(10));
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setLetterSpacing(0.04f);

        final GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(0);
        normal.setColor(0x33000000);
        normal.setStroke(dp(1), withAlpha(theme.mountainFar, 0x88));

        final GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(0);
        focused.setColor(theme.accentColor);
        focused.setStroke(dp(2), 0xFFFFFFFF);

        btn.setBackground(normal);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(7);
        btn.setLayoutParams(lp);

        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                focused.setColor(ThemeCatalog.get(prefs.getThemeIndex()).accentColor);
                v.setBackground(hasFocus ? focused : normal);
                btn.setTextColor(hasFocus ? 0xFF05050A : (isDestructive ? 0xFFFCA5A5 : 0xFFE2E8F0));
            }
        });
        btn.setOnClickListener(listener);
        return btn;
    }

    private void openAllAppsSideDrawer() {
        drawerBackAction = null;
        Set<String> hidden = prefs.getHiddenApps();
        List<AppInfo> visibleApps = new ArrayList<>();
        for (AppInfo app : allInstalledApps) {
            if (!hidden.contains(app.packageName)) {
                visibleApps.add(app);
            }
        }

        LinearLayout outer = createSideDrawerContainer(
                "ALL APPS (" + visibleApps.size() + ")",
                "OK to launch · Hold OK to pin or hide"
        );
        LinearLayout inner = getDrawerInner(outer);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(dp(4), dp(6), dp(4), dp(12));

        int tileW = dp(116);
        int tileH = dp(64);

        LinearLayout row = null;
        for (int i = 0; i < visibleApps.size(); i++) {
            int col = i % DRAWER_COLUMNS;
            if (col == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                rlp.bottomMargin = dp(14);
                row.setLayoutParams(rlp);
                grid.addView(row);
            }

            View card = createCompactAppCard(visibleApps.get(i), tileW, tileH, false, false);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (col < DRAWER_COLUMNS - 1) clp.rightMargin = dp(12);
            card.setLayoutParams(clp);
            row.addView(card);
        }

        sv.addView(grid);
        inner.addView(sv);

        showSideDrawer(outer, 430, false);
    }

    private void openAppOptionsSideDrawer(final AppInfo app, final boolean fromHomeDock) {
        drawerBackAction = null;
        LinearLayout outer = createSideDrawerContainer(app.label, "◀ Press Left or Back to close");
        LinearLayout inner = getDrawerInner(outer);

        final boolean isFav = prefs.isFavorite(app.packageName);

        if (isFav && fromHomeDock) {
            inner.addView(createSideMenuButton("◀ ▶  REORDER POSITION", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    enterReorderMode(app.packageName);
                }
            }));
        }

        inner.addView(createSideMenuButton(
                isFav ? "★  REMOVE FROM FAVORITES" : "☆  PIN TO FAVORITES",
                false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isFav) {
                            prefs.removeFavorite(app.packageName);
                        } else {
                            prefs.addFavorite(app.packageName);
                            pendingFocusPackage = app.packageName;
                        }
                        closeSideDrawer();
                        renderFavorites();
                    }
                }
        ));

        if (fromHomeDock) {
            inner.addView(createSideMenuButton("+  ALL APPS DRAWER", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openAllAppsSideDrawer();
                }
            }));
        }

        inner.addView(createSideMenuButton("APP INFO (SYSTEM)", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSideDrawer();
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + app.packageName));
                    startActivity(i);
                } catch (Exception ignored) {}
            }
        }));

        inner.addView(createSideMenuButton("HIDE APP", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.hideApp(app.packageName);
                closeSideDrawer();
                renderFavorites();
                renderResumeBar();
                Toast.makeText(MainActivity.this, app.label + " hidden", Toast.LENGTH_SHORT).show();
            }
        }));

        showSideDrawer(outer, 320, true);
    }

    private void openCustomizeSideDrawer() {
        drawerBackAction = null;
        LinearLayout outer = createSideDrawerContainer("CUSTOMIZE", "◀ Press Left or Back to close");
        LinearLayout inner = getDrawerInner(outer);

        int currentTheme = prefs.getThemeIndex();
        ThemeCatalog.ThemeSpec spec = ThemeCatalog.get(currentTheme);

        // 1. Dedicated Theme Selection Submenu!
        inner.addView(createSideMenuButton("🎨  THEMES: " + spec.name.toUpperCase(Locale.US) + "  ▶", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openThemesSideDrawer();
            }
        }));

        // 2. Display Settings Submenu!
        inner.addView(createSideMenuButton("📺  DISPLAY & CRT  ▶", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDisplaySettingsSideDrawer();
            }
        }));

        // 3. Layout & Dock Submenu!
        inner.addView(createSideMenuButton("📐  LAYOUT & DOCK  ▶", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLayoutSideDrawer();
            }
        }));

        // 4. Hidden Apps Submenu!
        Set<String> hidden = prefs.getHiddenApps();
        inner.addView(createSideMenuButton("👁  HIDDEN APPS (" + hidden.size() + ")  ▶", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHiddenAppsSideDrawer();
            }
        }));

        showSideDrawer(outer, 340, true);
    }

    private void openThemesSideDrawer() {
        drawerBackAction = new Runnable() {
            @Override
            public void run() {
                openCustomizeSideDrawer();
            }
        };
        LinearLayout outer = createSideDrawerContainer("THEMES", "◀ Press Left or Back to return");
        LinearLayout inner = getDrawerInner(outer);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        list.addView(createSideMenuButton("◀  BACK TO CUSTOMIZE", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCustomizeSideDrawer();
            }
        }));

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(1, dp(6)));
        list.addView(sp);

        int currentTheme = prefs.getThemeIndex();
        for (int i = 0; i < ThemeCatalog.THEMES.length; i++) {
            final int themeIdx = i;
            ThemeCatalog.ThemeSpec spec = ThemeCatalog.THEMES[i];
            String label = (i == currentTheme ? "●  " : "○  ") + spec.name.toUpperCase(Locale.US);
            list.addView(createSideMenuButton(label, false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.setThemeIndex(themeIdx);
                    applyTheme();
                    renderFavorites();
                    renderResumeBar();
                    openThemesSideDrawer();
                }
            }));
        }

        sv.addView(list);
        inner.addView(sv);

        showSideDrawer(outer, 340, true);
    }

    private void openDisplaySettingsSideDrawer() {
        drawerBackAction = new Runnable() {
            @Override
            public void run() {
                openCustomizeSideDrawer();
            }
        };
        LinearLayout outer = createSideDrawerContainer("DISPLAY", "◀ Press Left or Back to return");
        LinearLayout inner = getDrawerInner(outer);

        inner.addView(createSideMenuButton("◀  BACK TO CUSTOMIZE", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCustomizeSideDrawer();
            }
        }));

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(1, dp(6)));
        inner.addView(sp);

        final boolean animOn = prefs.isAnimateBg();
        inner.addView(createSideMenuButton("PIXEL SHIMMER: " + (animOn ? "ON" : "OFF"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setAnimateBg(!animOn);
                applyTheme();
                openDisplaySettingsSideDrawer();
            }
        }));

        final boolean scanOn = prefs.isScanlines();
        inner.addView(createSideMenuButton("CRT SCANLINES: " + (scanOn ? "ON" : "OFF"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setScanlines(!scanOn);
                applyTheme();
                openDisplaySettingsSideDrawer();
            }
        }));

        final boolean showLabels = prefs.isShowLabels();
        inner.addView(createSideMenuButton("APP CAPTION: " + (showLabels ? "ON" : "OFF"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setShowLabels(!showLabels);
                if (!prefs.isShowLabels()) focusedAppCaption.setText("");
                openDisplaySettingsSideDrawer();
            }
        }));

        showSideDrawer(outer, 340, true);
    }

    private void openLayoutSideDrawer() {
        drawerBackAction = new Runnable() {
            @Override
            public void run() {
                openCustomizeSideDrawer();
            }
        };
        LinearLayout outer = createSideDrawerContainer("LAYOUT", "◀ Press Left or Back to return");
        LinearLayout inner = getDrawerInner(outer);

        inner.addView(createSideMenuButton("◀  BACK TO CUSTOMIZE", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCustomizeSideDrawer();
            }
        }));

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(1, dp(6)));
        inner.addView(sp);

        int sizeMode = prefs.getIconSizeMode();
        String sizeName = sizeMode == 1 ? "TINY" : (sizeMode == 2 ? "MEDIUM" : "COMPACT");
        inner.addView(createSideMenuButton("ICON SIZE: " + sizeName, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setIconSizeMode(prefs.getIconSizeMode() + 1);
                renderFavorites();
                openLayoutSideDrawer();
            }
        }));

        int fontMode = prefs.getFontSizeMode();
        String fontName = fontMode == 1 ? "LARGE" : (fontMode == 2 ? "SMALL" : "REGULAR");
        inner.addView(createSideMenuButton("FONT SIZE: " + fontName, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setFontSizeMode(prefs.getFontSizeMode() + 1);
                applyFontSize();
                openLayoutSideDrawer();
            }
        }));

        final boolean showResume = prefs.isShowResumeBar();
        inner.addView(createSideMenuButton("RESUME BAR: " + (showResume ? "ON" : "OFF"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setShowResumeBar(!showResume);
                trayHaloView.setShowBottomHalo(!showResume);
                renderResumeBar();
                openLayoutSideDrawer();
            }
        }));

        showSideDrawer(outer, 340, true);
    }

    private void openHiddenAppsSideDrawer() {
        drawerBackAction = new Runnable() {
            @Override
            public void run() {
                openCustomizeSideDrawer();
            }
        };
        Set<String> hidden = prefs.getHiddenApps();
        LinearLayout outer = createSideDrawerContainer("HIDDEN APPS", "◀ Press Left or Back to return");
        LinearLayout inner = getDrawerInner(outer);

        inner.addView(createSideMenuButton("◀  BACK TO CUSTOMIZE", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCustomizeSideDrawer();
            }
        }));

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(1, dp(6)));
        inner.addView(sp);

        if (hidden.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No hidden apps.");
            empty.setTextColor(0x99FFF5E1);
            empty.setTypeface(pixelFont);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            empty.setPadding(0, dp(8), 0, dp(16));
            inner.addView(empty);
        } else {
            ScrollView sv = new ScrollView(this);
            sv.setVerticalScrollBarEnabled(false);
            sv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ));
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);

            for (final String pkg : hidden) {
                AppInfo info = appByPackage.get(pkg);
                String name = info != null ? info.label : pkg;
                list.addView(createSideMenuButton("UNHIDE: " + name.toUpperCase(Locale.getDefault()), false, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        prefs.unhideApp(pkg);
                        renderFavorites();
                        renderResumeBar();
                        openHiddenAppsSideDrawer();
                    }
                }));
            }
            sv.addView(list);
            inner.addView(sv);
        }

        showSideDrawer(outer, 340, true);
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private View findFirstFocusable(View view) {
        if (view.isFocusable()) return view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View found = findFirstFocusable(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void animateScale(View v, float scale, float elevation) {
        v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(115)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        v.setElevation(elevation);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()
        ));
    }

    /**
     * Draws two soft elliptical radial halos behind the right-justified Favorites
     * and Resume Watching trays that fade smoothly to 0% transparency in all directions
     * (no hard rectangular strip edges).
     */
    private static class SoftHaloView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int haloColor = 0xFF0A0818;
        private boolean showBottomHalo = true;

        SoftHaloView(Context context) {
            super(context);
        }

        void setHaloColor(int color) {
            this.haloColor = color & 0x00FFFFFF;
            invalidate();
        }

        void setShowBottomHalo(boolean show) {
            this.showBottomHalo = show;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            // 1. Soft elliptical halo behind the middle-right Favorites bar
            drawEllipticalHalo(canvas, w * 0.66f, h * 0.47f, w * 0.46f, h * 0.22f, 0xB8);

            // 2. Soft elliptical halo behind the bottom-right Resume Watching bar
            if (showBottomHalo) {
                drawEllipticalHalo(canvas, w * 0.72f, h * 0.87f, w * 0.38f, h * 0.21f, 0xC2);
            }
        }

        private void drawEllipticalHalo(Canvas canvas, float cx, float cy, float rx, float ry, int centerAlpha) {
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(1f, ry / rx);

            int c0 = (centerAlpha << 24) | haloColor;
            int c1 = ((centerAlpha / 2) << 24) | haloColor;
            int c2 = 0x00000000;

            RadialGradient rg = new RadialGradient(
                    0f, 0f, rx,
                    new int[] { c0, c1, c2 },
                    new float[] { 0.0f, 0.55f, 1.0f },
                    Shader.TileMode.CLAMP
            );
            paint.setShader(rg);
            canvas.drawCircle(0f, 0f, rx, paint);
            canvas.restore();
        }
    }
}
