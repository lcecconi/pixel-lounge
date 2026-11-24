package com.essential.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PrefsManager {
    private static final String PREFS_NAME = "essential_launcher_prefs";
    private static final String KEY_FAVORITES = "favorites_order";
    private static final String KEY_FAVORITES_INIT = "favorites_initialized";
    private static final String KEY_HIDDEN = "hidden_apps";
    private static final String KEY_RECENT = "recent_apps";
    private static final String KEY_THEME = "theme_index";
    private static final String KEY_SHOW_LABELS = "show_labels";
    private static final String KEY_ANIMATE_BG = "animate_bg";
    private static final String KEY_SCANLINES = "scanlines";
    private static final String KEY_SHOW_RESUME = "show_resume_bar";
    private static final String KEY_ICON_SIZE = "icon_size_mode"; // 0=Compact, 1=Tiny, 2=Medium
    private static final String KEY_FONT_SIZE = "font_size_mode"; // 0=Regular, 1=Large, 2=Small

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFavoritesInitialized() {
        return prefs.getBoolean(KEY_FAVORITES_INIT, false);
    }

    public List<String> getFavorites() {
        return parseCsv(prefs.getString(KEY_FAVORITES, ""));
    }

    public void saveFavorites(List<String> favorites) {
        prefs.edit()
                .putString(KEY_FAVORITES, toCsv(favorites))
                .putBoolean(KEY_FAVORITES_INIT, true)
                .apply();
    }

    public boolean isFavorite(String packageName) {
        return getFavorites().contains(packageName);
    }

    public void addFavorite(String packageName) {
        List<String> favs = getFavorites();
        if (!favs.contains(packageName)) {
            favs.add(packageName);
            saveFavorites(favs);
        }
    }

    public void removeFavorite(String packageName) {
        List<String> favs = getFavorites();
        if (favs.remove(packageName)) {
            saveFavorites(favs);
        }
    }

    public boolean moveFavorite(String packageName, int delta) {
        List<String> favs = getFavorites();
        int idx = favs.indexOf(packageName);
        if (idx < 0) return false;
        int target = idx + delta;
        if (target < 0 || target >= favs.size()) return false;
        favs.remove(idx);
        favs.add(target, packageName);
        saveFavorites(favs);
        return true;
    }

    public void recordRecentApp(String packageName) {
        List<String> recent = getRecentApps();
        recent.remove(packageName);
        recent.add(0, packageName);
        while (recent.size() > 6) {
            recent.remove(recent.size() - 1);
        }
        prefs.edit().putString(KEY_RECENT, toCsv(recent)).apply();
    }

    public List<String> getRecentApps() {
        return parseCsv(prefs.getString(KEY_RECENT, ""));
    }

    public Set<String> getHiddenApps() {
        Set<String> stored = prefs.getStringSet(KEY_HIDDEN, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public void hideApp(String packageName) {
        Set<String> hidden = getHiddenApps();
        hidden.add(packageName);
        prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply();
        removeFavorite(packageName);
    }

    public void unhideApp(String packageName) {
        Set<String> hidden = getHiddenApps();
        if (hidden.remove(packageName)) {
            prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply();
        }
    }

    public int getThemeIndex() {
        return prefs.getInt(KEY_THEME, 0);
    }

    public void setThemeIndex(int index) {
        prefs.edit().putInt(KEY_THEME, index).apply();
    }

    public boolean isShowLabels() {
        return prefs.getBoolean(KEY_SHOW_LABELS, true);
    }

    public void setShowLabels(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_LABELS, show).apply();
    }

    public boolean isAnimateBg() {
        return prefs.getBoolean(KEY_ANIMATE_BG, true);
    }

    public void setAnimateBg(boolean animate) {
        prefs.edit().putBoolean(KEY_ANIMATE_BG, animate).apply();
    }

    public boolean isScanlines() {
        return prefs.getBoolean(KEY_SCANLINES, true);
    }

    public void setScanlines(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCANLINES, enabled).apply();
    }

    public boolean isShowResumeBar() {
        return prefs.getBoolean(KEY_SHOW_RESUME, true);
    }

    public void setShowResumeBar(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_RESUME, show).apply();
    }

    public int getIconSizeMode() {
        return prefs.getInt(KEY_ICON_SIZE, 0);
    }

    public void setIconSizeMode(int mode) {
        prefs.edit().putInt(KEY_ICON_SIZE, (mode % 3 + 3) % 3).apply();
    }

    public int getFontSizeMode() {
        return prefs.getInt(KEY_FONT_SIZE, 0);
    }

    public void setFontSizeMode(int mode) {
        prefs.edit().putInt(KEY_FONT_SIZE, (mode % 3 + 3) % 3).apply();
    }

    private List<String> parseCsv(String raw) {
        List<String> list = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String item : raw.split(",")) {
                if (!item.isEmpty() && !list.contains(item)) {
                    list.add(item);
                }
            }
        }
        return list;
    }

    private String toCsv(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
