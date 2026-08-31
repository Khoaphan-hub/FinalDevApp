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
        {"Chợ Đà Lạt", "Da Lat Market", "Nguyễn Thị Minh Khai, Phường 1", "Nguyen Thi Minh Khai, Ward 1"},
        {"Hồ Xuân Hương", "Xuan Huong Lake", "Trung tâm thành phố Đà Lạt", "Da Lat city centre"},
        {"Vườn hoa thành phố", "Da Lat Flower Park", "Trần Quốc Toản, Phường 8", "Tran Quoc Toan, Ward 8"},
        {"Dinh Bảo Đại", "Bao Dai Palace", "Triệu Việt Vương, Phường 4", "Trieu Viet Vuong, Ward 4"},
        {"Thiền viện Trúc Lâm", "Truc Lam Monastery", "Đường Trúc Lâm Yên Tử, Phường 3", "Truc Lam Yen Tu Road, Ward 3"},
        {"Đồi chè Cầu Đất", "Cau Dat Tea Hill", "Xuân Trường, Đà Lạt", "Xuan Truong, Da Lat"},
        {"Ga Đà Lạt", "Da Lat Railway Station", "Quang Trung, Phường 9", "Quang Trung, Ward 9"},
        {"Nhà thờ Domaine de Marie", "Domaine de Marie Church", "Ngô Quyền, Phường 6", "Ngo Quyen, Ward 6"},
        {"Quảng trường Lâm Viên", "Lam Vien Square", "Trần Quốc Toản, Phường 1", "Tran Quoc Toan, Ward 1"}
    };

    @Override
    public void generate(TripRequest request, RepositoryCallback<Itinerary> callback) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            List<ItineraryDay> days = new ArrayList<>();
            int cursor = 0;
            boolean english = "en".equals(java.util.Locale.getDefault().getLanguage());
            String accommodation = request.isUseDefaultCenter() || request.getStartAddress().isEmpty()
                ? (english ? "Da Lat Market" : "Chợ Đà Lạt") : request.getStartAddress();

            for (int day = 1; day <= request.getDays(); day++) {
                List<ItineraryStop> stops = new ArrayList<>();
                stops.add(stop(0, ItineraryStop.Type.ACCOMMODATION, accommodation,
                    english ? "Starting point" : "Điểm bắt đầu", 1.1, null));
                for (int index = 0; index < request.getDailyPoiLimit(); index++) {
                    String[] place = PLACES[cursor++ % PLACES.length];
                    stops.add(stop(cursor, ItineraryStop.Type.POI, place[english ? 1 : 0], place[english ? 3 : 2],
                        1.3 + (index * 0.7), null));
                    if (index == Math.max(0, request.getDailyPoiLimit() / 2 - 1)) {
                        stops.add(stop(100 + day, ItineraryStop.Type.EATERY, english ? "Moc Home Kitchen" : "Bếp Nhà Mộc",
                            english ? "Alley 2 Dang Thai Than, Ward 3" : "Hẻm 2 Đặng Thái Thân, Phường 3", 1.8, "afternoon"));
                    }
                }
                stops.add(stop(0, ItineraryStop.Type.ACCOMMODATION, accommodation,
                    english ? "Return to accommodation" : "Trở về điểm nghỉ", 0, null));
                days.add(new ItineraryDay(day, stops));
            }

            long estimated = Math.min(request.getBudgetVnd(),
                request.getDays() * (350_000L + request.getDailyPoiLimit() * 90_000L));
            callback.onSuccess(new Itinerary(english ? "Da Lat, your way" : "Đà Lạt theo cách của bạn", days,
                request.getBudgetVnd(), estimated, true));
        }, 850);
    }

    private ItineraryStop stop(int id, ItineraryStop.Type type, String name, String address,
                               double distance, String slot) {
        return new ItineraryStop(id, type, name, address, 11.94, 108.45, distance, slot);
    }
}
