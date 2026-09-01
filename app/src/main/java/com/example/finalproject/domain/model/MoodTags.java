package com.example.finalproject.domain.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps a trip mood to the catalog tags that describe it.
 *
 * This mirrors TRAVEL_MOOD_TAGS in the Django backend (`home/constants.py`). It is duplicated
 * on purpose: the offline planner has to pick places without asking the server, and both copies
 * must be edited together if the vocabulary ever changes.
 */
public final class MoodTags {
    private MoodTags() { }

    public static List<String> forMood(Mood mood) {
        switch (mood) {
            case RELAXED: return Arrays.asList("pure nature", "50% nature", "healing", "spiritual");
            case ACTIVE: return Arrays.asList("sporty", "50% nature");
            case ROMANTIC: return Arrays.asList("cafe", "eating", "50% human");
            case FOODIE: return Arrays.asList("eating", "cafe");
            case CULTURE: return Arrays.asList("history", "manmade", "50% human");
            case SOCIAL: return Arrays.asList("cafe", "eating", "50% human");
            case SHOPPING: return Arrays.asList("manmade", "50% human");
            case HEALING: return Arrays.asList("healing", "pure nature", "spiritual");
            case BIZARRE: return Arrays.asList("bizarre");
            default: return new ArrayList<>();
        }
    }

    /** Every tag wanted by the selected moods, lower-cased and de-duplicated. */
    public static Set<String> forMoods(List<Mood> moods) {
        Set<String> tags = new LinkedHashSet<>();
        for (Mood mood : moods) tags.addAll(forMood(mood));
        return tags;
    }

    /** True when a place's comma separated tag string mentions any of the wanted tags. */
    public static boolean matches(String placeTags, Set<String> wanted) {
        if (placeTags == null || placeTags.trim().isEmpty() || wanted.isEmpty()) return false;
        String haystack = placeTags.toLowerCase(Locale.ROOT);
        for (String tag : wanted) {
            if (haystack.contains(tag)) return true;
        }
        return false;
    }
}
