package com.essential.tvlauncher;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String packageName;
    public final String activityName;
    public final String label;
    public final Drawable banner;
    public final Drawable icon;
    public final boolean isLeanback;

    public AppInfo(String packageName, String activityName, String label,
                   Drawable banner, Drawable icon, boolean isLeanback) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
        this.isLeanback = isLeanback;
    }
}
