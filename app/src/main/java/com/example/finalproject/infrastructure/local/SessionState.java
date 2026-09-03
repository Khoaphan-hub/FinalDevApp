package com.example.finalproject.infrastructure.local;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers whether the user signed in, so screens can ask without going to the server.
 *
 * The session itself lives in the process-wide CookieManager, which is memory only: it is gone
 * the moment the app is killed. That is fine for talking to Django, which decides for itself
 * whether a request is authenticated, but it gives the UI nothing to read when it merely needs
 * to know which state to draw, and nothing at all when there is no network.
 *
 * This flag fills that gap. It is a UI hint, never an authorisation check: anything the server
 * protects is still protected by the server. When the two disagree, the server wins and the
 * screen corrects itself, which is why a 401 clears the flag.
 */
public final class SessionState {
    private static final String PREFERENCES = "journify_session";
    private static final String KEY_SIGNED_IN = "signed_in";

    private SessionState() { }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public static boolean isSignedIn(Context context) {
        return preferences(context).getBoolean(KEY_SIGNED_IN, false);
    }

    public static void markSignedIn(Context context) {
        preferences(context).edit().putBoolean(KEY_SIGNED_IN, true).apply();
    }

    /** Also called when the server answers 401, so a stale flag cannot outlive the session. */
    public static void markSignedOut(Context context) {
        preferences(context).edit().putBoolean(KEY_SIGNED_IN, false).apply();
    }
}
