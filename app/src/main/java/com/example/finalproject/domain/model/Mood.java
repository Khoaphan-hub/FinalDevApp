package com.example.finalproject.domain.model;

public enum Mood {
    RELAXED("Relaxed", "Thư giãn"), ACTIVE("Active", "Năng động"),
    ROMANTIC("Romantic", "Lãng mạn"), FOODIE("Foodie", "Ẩm thực"),
    CULTURE("Culture", "Văn hoá"), SOCIAL("Social", "Kết nối"),
    SHOPPING("Shopping", "Mua sắm"), HEALING("Healing", "Chữa lành"),
    BIZARRE("Bizarre", "Khác lạ");

    private final String apiValue;
    private final String displayName;

    Mood(String apiValue, String displayName) {
        this.apiValue = apiValue;
        this.displayName = displayName;
    }

    public String getApiValue() { return apiValue; }
    public String getDisplayName() { return displayName; }
}
