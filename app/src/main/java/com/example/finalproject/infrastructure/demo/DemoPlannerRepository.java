package com.example.finalproject.infrastructure.demo;

import android.os.Handler;
import android.os.Looper;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.ItineraryDay;
import com.example.finalproject.domain.model.ItineraryStop;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.domain.repository.PlannerRepository;

import java.util.ArrayList;
import java.util.List;

public final class DemoPlannerRepository implements PlannerRepository {
    private static final String[][] PLACES = {
        {"Chợ Đà Lạt", "Nguyễn Thị Minh Khai, Phường 1"},
        {"Hồ Xuân Hương", "Trung tâm thành phố Đà Lạt"},
        {"Vườn hoa thành phố", "Trần Quốc Toản, Phường 8"},
        {"Dinh Bảo Đại", "Triệu Việt Vương, Phường 4"},
        {"Thiền viện Trúc Lâm", "Đường Trúc Lâm Yên Tử, Phường 3"},
        {"Đồi chè Cầu Đất", "Xuân Trường, Đà Lạt"},
        {"Ga Đà Lạt", "Quang Trung, Phường 9"},
        {"Nhà thờ Domaine de Marie", "Ngô Quyền, Phường 6"},
        {"Quảng trường Lâm Viên", "Trần Quốc Toản, Phường 1"}
    };

    @Override
    public void generate(TripRequest request, RepositoryCallback<Itinerary> callback) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            List<ItineraryDay> days = new ArrayList<>();
            int cursor = 0;
            String accommodation = request.isUseDefaultCenter() || request.getStartAddress().isEmpty()
                ? "Chợ Đà Lạt" : request.getStartAddress();

            for (int day = 1; day <= request.getDays(); day++) {
                List<ItineraryStop> stops = new ArrayList<>();
                stops.add(stop(0, ItineraryStop.Type.ACCOMMODATION, accommodation,
                    "Điểm bắt đầu", 1.1, null));
                for (int index = 0; index < request.getDailyPoiLimit(); index++) {
                    String[] place = PLACES[cursor++ % PLACES.length];
                    stops.add(stop(cursor, ItineraryStop.Type.POI, place[0], place[1],
                        1.3 + (index * 0.7), null));
                    if (index == Math.max(0, request.getDailyPoiLimit() / 2 - 1)) {
                        stops.add(stop(100 + day, ItineraryStop.Type.EATERY, "Bếp Nhà Mộc",
                            "Hẻm 2 Đặng Thái Thân, Phường 3", 1.8, "afternoon"));
                    }
                }
                stops.add(stop(0, ItineraryStop.Type.ACCOMMODATION, accommodation,
                    "Trở về điểm nghỉ", 0, null));
                days.add(new ItineraryDay(day, stops));
            }

            long estimated = Math.min(request.getBudgetVnd(),
                request.getDays() * (350_000L + request.getDailyPoiLimit() * 90_000L));
            callback.onSuccess(new Itinerary("Đà Lạt theo cách của bạn", days,
                request.getBudgetVnd(), estimated, true));
        }, 850);
    }

    private ItineraryStop stop(int id, ItineraryStop.Type type, String name, String address,
                               double distance, String slot) {
        return new ItineraryStop(id, type, name, address, 11.94, 108.45, distance, slot);
    }
}
