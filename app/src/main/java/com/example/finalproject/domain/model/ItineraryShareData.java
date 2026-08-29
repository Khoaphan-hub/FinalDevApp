package com.example.finalproject.domain.model;

public final class ItineraryShareData {
    private final String shareUrl;
    private final String qrBase64;
    private final String expiresAt;

    public ItineraryShareData(String shareUrl, String qrBase64, String expiresAt) {
        this.shareUrl = shareUrl;
        this.qrBase64 = qrBase64;
        this.expiresAt = expiresAt;
    }
    public String getShareUrl() { return shareUrl; }
    public String getQrBase64() { return qrBase64; }
    public String getExpiresAt() { return expiresAt; }
}
