package com.example.finalproject.infrastructure.link;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExternalPlaceLinksTest {
    @Test public void tiktokReviewKeepsAtSignAndDropsTrackingQuery() {
        String source = "https://www.tiktok.com/@ngocduy_diachianuong/video/7304961587364777224"
            + "?q=Ti%E1%BB%87m&t=1761489656187";

        String result = ExternalPlaceLinks.cleanReviewUrl(source);

        assertEquals("https://www.tiktok.com/@ngocduy_diachianuong/video/7304961587364777224",
            result);
        assertFalse(result.contains("%40"));
    }

    @Test public void nonTikTokReviewRemainsUnchanged() {
        String source = "https://maps.app.goo.gl/example?share=1";
        assertEquals(source, ExternalPlaceLinks.cleanReviewUrl(source));
    }

    @Test public void mapsSearchUsesPlaceNameAndAddress() {
        String result = ExternalPlaceLinks.googleMapsSearchUrl(
            "Tiệm ăn Bếp Đà Lạt", "89 Hoàng Hoa Thám, Phường 10, Đà Lạt",
            11.9336841, 108.4648486);

        assertTrue(result.startsWith("https://www.google.com/maps/search/?api=1&query="));
        assertTrue(result.contains("Ti%E1%BB%87m%20%C4%83n%20B%E1%BA%BFp%20%C4%90%C3%A0%20L%E1%BA%A1t"));
        assertTrue(result.contains("89%20Ho%C3%A0ng%20Hoa%20Th%C3%A1m"));
        assertFalse(result.contains("11.9336841"));
    }

    @Test public void mapsSearchFallsBackToCoordinatesWhenTextIsMissing() {
        String result = ExternalPlaceLinks.googleMapsSearchUrl(" ", null,
            11.9336841, 108.4648486);

        assertTrue(result.endsWith("11.9336841%2C108.4648486"));
    }
}
