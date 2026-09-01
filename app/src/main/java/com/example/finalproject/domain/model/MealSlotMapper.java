package com.example.finalproject.domain.model;
import com.example.finalproject.R;
import java.util.Locale;
public final class MealSlotMapper {
    private MealSlotMapper() {}
    /** Maps the raw backend slot value to a localized label resource, or 0 when unknown. */
    public static int labelRes(String slot){if(slot==null)return 0;String s=slot.trim().toLowerCase(Locale.ROOT);if(s.equals("morning"))return R.string.slot_morning;if(s.equals("afternoon"))return R.string.slot_afternoon;if(s.equals("evening"))return R.string.slot_evening;return 0;}
}
