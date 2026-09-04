package com.example.finalproject.domain.model;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a token only. A scanned URL is never used as a network destination. */
public final class ItineraryQrLink {
    private static final Pattern PATH = Pattern.compile("^/resume/([A-Za-z0-9_-]{16,48})/?$");
    private ItineraryQrLink() { }

    public static String token(String contents) {
        if (contents == null || contents.length() > 2048) throw new IllegalArgumentException("INVALID_QR");
        try {
            URI uri = new URI(contents.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("INVALID_QR");
            }
            Matcher matcher = PATH.matcher(uri.getRawPath());
            if (!matcher.matches()) throw new IllegalArgumentException("INVALID_QR");
            return matcher.group(1);
        } catch (java.net.URISyntaxException error) {
            throw new IllegalArgumentException("INVALID_QR", error);
        }
    }
}
