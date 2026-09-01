package com.example.finalproject;

import android.app.Application;
import java.net.CookieHandler;
import java.net.CookieManager;

public class JournifyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize global CookieManager so HttpURLConnection retains session cookies automatically.
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);
    }
}
