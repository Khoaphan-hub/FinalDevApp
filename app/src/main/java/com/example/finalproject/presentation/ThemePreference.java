package com.example.finalproject.presentation;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Remembers whether the user asked for the dark theme.
 *
 * Three states rather than a boolean: until someone touches the switch the app follows the
 * device setting, which is what most Android users expect. Once they choose, that choice wins
 * and survives a restart.
 */
public final class ThemePreference {
    private static final String PREFS = "journify_appearance";
    private static final String KEY_MODE = "night_mode";

    /** Matches AppCompatDelegate constants so the stored value can be applied directly. */
    private static final int FOLLOW_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

    private ThemePreference() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Call once on startup, before any activity inflates its layout. */
    public static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(storedMode(context));
    }

    public static int storedMode(Context context) {
        return prefs(context).getInt(KEY_MODE, FOLLOW_SYSTEM);
    }

    /** True when dark is currently in effect, either by choice or by following the device. */
    public static boolean isDark(Context context) {
        int mode = storedMode(context);
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return true;
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return false;
        int night = context.getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** Stores the choice and applies it; activities recreate themselves to pick up the new theme. */
    public static void setDark(Context context, boolean dark) {
        int mode = dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        prefs(context).edit().putInt(KEY_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
