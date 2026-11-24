package com.essential.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppLoader {

    private static final Set<String> EXCLUDED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.essential.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher"
    ));

    private static final List<String> DEFAULT_FAVORITES_PRIORITY = Arrays.asList(
            "org.jellyfin.androidtv",
            "com.plexapp.android",
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "nl.giejay.android.tv.immich",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
            "ca.devmesh.overseerrtv",
            "it.rainet.androidtv"
    );

    public static List<AppInfo> loadInstalledApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, AppInfo> appMap = new HashMap<>();

        // 1. Query native Android TV (Leanback) apps first
        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanbackList = pm.queryIntentActivities(leanbackIntent, 0);
        for (ResolveInfo ri : leanbackList) {
            addResolveInfo(pm, ri, true, appMap);
        }

        // 2. Query standard launcher apps (includes sideloaded mobile apps)
        Intent standardIntent = new Intent(Intent.ACTION_MAIN, null);
        standardIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standardList = pm.queryIntentActivities(standardIntent, 0);
        for (ResolveInfo ri : standardList) {
            if (!appMap.containsKey(ri.activityInfo.packageName)) {
                addResolveInfo(pm, ri, false, appMap);
            }
        }

        List<AppInfo> result = new ArrayList<>(appMap.values());
        Collections.sort(result, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return result;
    }

    private static void addResolveInfo(PackageManager pm, ResolveInfo ri,
                                       boolean isLeanback, Map<String, AppInfo> map) {
        if (ri == null || ri.activityInfo == null) return;
        String pkg = ri.activityInfo.packageName;
        if (pkg == null || EXCLUDED_PACKAGES.contains(pkg)) return;

        CharSequence labelCs = ri.loadLabel(pm);
        String label = (labelCs != null && labelCs.length() > 0) ? labelCs.toString() : pkg;

        Drawable banner = null;
        try {
            banner = ri.activityInfo.loadBanner(pm);
            if (banner == null && ri.activityInfo.applicationInfo != null) {
                banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            }
        } catch (Exception ignored) {}

        Drawable icon = null;
        try {
            icon = ri.loadIcon(pm);
        } catch (Exception ignored) {}

        map.put(pkg, new AppInfo(pkg, ri.activityInfo.name, label, banner, icon, isLeanback));
    }

    public static void ensureInitialFavorites(PrefsManager prefs, List<AppInfo> allApps) {
        if (prefs.isFavoritesInitialized()) return;

        Set<String> installed = new HashSet<>();
        for (AppInfo app : allApps) {
            installed.add(app.packageName);
        }

        List<String> seeded = new ArrayList<>();
        for (String pkg : DEFAULT_FAVORITES_PRIORITY) {
            if (installed.contains(pkg) && seeded.size() < 6) {
                seeded.add(pkg);
            }
        }
        if (seeded.isEmpty()) {
            for (int i = 0; i < Math.min(6, allApps.size()); i++) {
                seeded.add(allApps.get(i).packageName);
            }
        }
        prefs.saveFavorites(seeded);
    }
}
