package com.vectras.vm.settings;

import android.content.Context;
import androidx.preference.PreferenceManager;

public final class VncDisplayConfig {
    private static final String KEY_FORCE_REFRESH = "forceRefeshVNCDisplay";

    private VncDisplayConfig() {}

    public static void setForceRefresh(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean(KEY_FORCE_REFRESH, enabled).apply();
    }

    public static boolean getForceRefresh(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_FORCE_REFRESH, true);
    }
}
