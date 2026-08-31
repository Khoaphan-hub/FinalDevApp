package com.example.finalproject.application.usecase;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.domain.repository.PlannerRepository;

public final class GenerateItineraryUseCase {
    private final PlannerRepository repository;

    public GenerateItineraryUseCase(PlannerRepository repository) {
        this.repository = repository;
    }

    public void execute(TripRequest request, RepositoryCallback<Itinerary> callback) {
        if (request.getDays() < 1 || request.getDays() > 7) {
            callback.onError(new IllegalArgumentException(message("Days must be between 1 and 7.", "Số ngày phải từ 1 đến 7.")));
            return;
        }
        if (request.getDailyPoiLimit() < 1 || request.getDailyPoiLimit() > 6) {
            callback.onError(new IllegalArgumentException(message("Places per day must be between 1 and 6.", "Số địa điểm mỗi ngày phải từ 1 đến 6.")));
            return;
        }
        if (request.getBudgetVnd() <= 0) {
            callback.onError(new IllegalArgumentException(message("Budget must be greater than zero.", "Ngân sách phải lớn hơn 0.")));
            return;
        }
        if (request.getMoods().isEmpty()) {
            callback.onError(new IllegalArgumentException(message("Choose at least one trip mood.", "Hãy chọn ít nhất một tâm trạng.")));
            return;
        }
        repository.generate(request, callback);
    }

    private String message(String english, String vietnamese) {
        return "en".equals(java.util.Locale.getDefault().getLanguage()) ? english : vietnamese;
    }
}
