package com.example.finalproject;

import android.app.Application;
import com.example.finalproject.infrastructure.local.PersistentCookieStore;
import com.example.finalproject.presentation.ThemePreference;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

public class JournifyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Applied before any activity inflates, so the very first screen already uses the
        // theme the user picked rather than flashing the other one first.
        ThemePreference.apply(this);
        
        // Every HttpURLConnection in the app shares this manager, which is how the Django
        // session cookie is carried without each caller handling headers itself. The store is
        // backed by SharedPreferences so the session outlives the process: Django issues
        // sessionid with a two week Max-Age, and dropping it on every restart made the user
        // sign in again far sooner than the server ever asked.
        CookieManager cookieManager = new CookieManager(
            new PersistentCookieStore(this), CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        CookieHandler.setDefault(cookieManager);
    }
}
