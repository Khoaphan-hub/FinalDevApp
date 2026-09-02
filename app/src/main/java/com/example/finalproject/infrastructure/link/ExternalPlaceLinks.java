package com.example.finalproject.infrastructure.link;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Locale;

public final class ExternalPlaceLinks {
    private static final String MAPS_SEARCH_BASE =
        "https://www.google.com/maps/search/?api=1&query=";

    private ExternalPlaceLinks() {}

    public static String cleanReviewUrl(String value) {
        if (isBlank(value)) return "";
        String trimmed = value.trim();
        try {
            String host = new URI(trimmed).getHost();
            if (host != null && isTikTokHost(host)) {
                int end = trimmed.length();
                int query = trimmed.indexOf('?');
                int fragment = trimmed.indexOf('#');
                if (query >= 0) end = Math.min(end, query);
                if (fragment >= 0) end = Math.min(end, fragment);
                return trimmed.substring(0, end);
            }
        } catch (Exception ignored) {
            // Keep malformed or non-standard review links unchanged.
        }
        return trimmed;
    }

    public static String googleMapsSearchUrl(String name, String address,
                                             double latitude, double longitude) {
        String query = joinNonBlank(name, address);
        if (isBlank(query)) {
            query = String.format(Locale.US, "%.7f,%.7f", latitude, longitude);
        }
        return MAPS_SEARCH_BASE + encodeQuery(query);
    }

    private static boolean isTikTokHost(String host) {
        String lower = host.toLowerCase(Locale.US);
        return lower.equals("tiktok.com") || lower.endsWith(".tiktok.com");
    }

    private static String joinNonBlank(String name, String address) {
        if (isBlank(name)) return isBlank(address) ? "" : address.trim();
        if (isBlank(address)) return name.trim();
        return name.trim() + ", " + address.trim();
    }

    private static String encodeQuery(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException impossible) {
            return value;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
