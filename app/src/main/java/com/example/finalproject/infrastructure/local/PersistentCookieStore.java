package com.example.finalproject.infrastructure.local;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;

/**
 * A CookieStore that survives the app being killed.
 *
 * The default store java.net.CookieManager builds is memory only, so every restart logged the
 * user out even though Django had issued a two week session. Django sends sessionid with
 * Max-Age 1209600 and csrftoken with Max-Age 31449600, both explicitly dated, so keeping them
 * on disk honours the lifetime the server asked for rather than inventing one.
 *
 * Cookies are held in a normal in-memory store, which does the matching and domain rules, and
 * every change is mirrored to SharedPreferences. Expiry is written as an absolute timestamp
 * because HttpCookie.getMaxAge() counts down from whenever it is asked.
 */
public final class PersistentCookieStore implements CookieStore {
    private static final String PREFERENCES = "journify_cookies";
    private static final String KEY_COOKIES = "cookies";

    private final SharedPreferences preferences;
    private final CookieStore memory = new CookieManager().getCookieStore();

    public PersistentCookieStore(Context context) {
        this.preferences = context.getApplicationContext()
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        restore();
    }

    /** Drops every stored cookie. Used when signing out. */
    public static void clear(Context context) {
        java.net.CookieHandler handler = java.net.CookieHandler.getDefault();
        if (handler instanceof CookieManager) {
            ((CookieManager) handler).getCookieStore().removeAll();
        }
        context.getApplicationContext()
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().remove(KEY_COOKIES).apply();
    }

    private void restore() {
        String stored = preferences.getString(KEY_COOKIES, null);
        if (stored == null) return;
        long now = System.currentTimeMillis();
        try {
            JSONArray array = new JSONArray(stored);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                long expiresAt = entry.optLong("expiresAt", 0);
                // An expired cookie is simply dropped rather than handed back to the server.
                if (expiresAt <= now) continue;

                HttpCookie cookie = new HttpCookie(entry.getString("name"), entry.optString("value"));
                if (!entry.isNull("domain")) cookie.setDomain(entry.optString("domain"));
                if (!entry.isNull("path")) cookie.setPath(entry.optString("path"));
                cookie.setSecure(entry.optBoolean("secure"));
                cookie.setHttpOnly(entry.optBoolean("httpOnly"));
                cookie.setVersion(entry.optInt("version"));
                cookie.setMaxAge((expiresAt - now) / 1000L);
                memory.add(URI.create(entry.getString("uri")), cookie);
            }
        } catch (Exception unreadable) {
            // A corrupt or outdated store just means signing in again; never crash on it.
            preferences.edit().remove(KEY_COOKIES).apply();
        }
    }

    private void persist() {
        long now = System.currentTimeMillis();
        JSONArray array = new JSONArray();
        try {
            for (URI uri : memory.getURIs()) {
                for (HttpCookie cookie : memory.get(uri)) {
                    // Max age -1 means "forget when the client closes"; there is nothing to keep.
                    if (cookie.getMaxAge() <= 0) continue;
                    JSONObject entry = new JSONObject();
                    entry.put("uri", uri.toString());
                    entry.put("name", cookie.getName());
                    entry.put("value", cookie.getValue());
                    entry.put("domain", cookie.getDomain());
                    entry.put("path", cookie.getPath());
                    entry.put("secure", cookie.getSecure());
                    entry.put("httpOnly", cookie.isHttpOnly());
                    entry.put("version", cookie.getVersion());
                    entry.put("expiresAt", now + cookie.getMaxAge() * 1000L);
                    array.put(entry);
                }
            }
            preferences.edit().putString(KEY_COOKIES, array.toString()).apply();
        } catch (Exception ignored) {
            // Failing to save only costs the user another sign-in later.
        }
    }

    @Override public void add(URI uri, HttpCookie cookie) {
        memory.add(uri, cookie);
        persist();
    }

    @Override public List<HttpCookie> get(URI uri) { return memory.get(uri); }

    @Override public List<HttpCookie> getCookies() { return memory.getCookies(); }

    @Override public List<URI> getURIs() { return memory.getURIs(); }

    @Override public boolean remove(URI uri, HttpCookie cookie) {
        boolean removed = memory.remove(uri, cookie);
        if (removed) persist();
        return removed;
    }

    @Override public boolean removeAll() {
        boolean removed = memory.removeAll();
        preferences.edit().remove(KEY_COOKIES).apply();
        return removed;
    }
}
