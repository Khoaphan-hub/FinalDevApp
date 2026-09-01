package com.example.finalproject.application.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.Mood;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.domain.repository.PlannerRepository;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The use case guards the request before any network call, so an invalid trip must never
 * reach the repository. These tests pin each guard down with a fake repository that records
 * whether it was asked to do any work.
 */
public class GenerateItineraryUseCaseTest {

    /** Stands in for the remote planner; never performs I/O. */
    private static final class RecordingRepository implements PlannerRepository {
        boolean called;

        @Override public void generate(TripRequest request, RepositoryCallback<Itinerary> callback) {
            called = true;
            callback.onSuccess(new Itinerary("stub", new ArrayList<>(), 0L, 0L, false));
        }
    }

    private static final class Result {
        Itinerary success;
        Exception failure;
    }

    private static TripRequest request(int days, int dailyPoiLimit, long budget, List<Mood> moods) {
        return new TripRequest(days, dailyPoiLimit, budget, moods, "", true,
            Collections.emptyList(), Collections.emptyList());
    }

    private static TripRequest validRequest() {
        return request(3, 3, 3_000_000L, Arrays.asList(Mood.RELAXED, Mood.FOODIE));
    }

    private Result run(RecordingRepository repository, TripRequest request) {
        Result result = new Result();
        new GenerateItineraryUseCase(repository).execute(request, new RepositoryCallback<Itinerary>() {
            @Override public void onSuccess(Itinerary itinerary) { result.success = itinerary; }
            @Override public void onError(Exception error) { result.failure = error; }
        });
        return result;
    }

    @Test public void passesAValidRequestToTheRepository() {
        RecordingRepository repository = new RecordingRepository();
        Result result = run(repository, validRequest());

        assertTrue("a valid request must reach the repository", repository.called);
        assertEquals("stub", result.success.getTitle());
    }

    @Test public void rejectsDaysOutsideTheSupportedRange() {
        for (int days : new int[]{0, 8}) {
            RecordingRepository repository = new RecordingRepository();
            Result result = run(repository, request(days, 3, 3_000_000L,
                Collections.singletonList(Mood.RELAXED)));

            assertFalse("days=" + days + " must not reach the repository", repository.called);
            assertTrue(result.failure instanceof IllegalArgumentException);
        }
    }

    @Test public void rejectsPlacesPerDayOutsideTheSupportedRange() {
        for (int limit : new int[]{0, 7}) {
            RecordingRepository repository = new RecordingRepository();
            Result result = run(repository, request(3, limit, 3_000_000L,
                Collections.singletonList(Mood.RELAXED)));

            assertFalse("dailyPoiLimit=" + limit + " must not reach the repository", repository.called);
            assertTrue(result.failure instanceof IllegalArgumentException);
        }
    }

    @Test public void rejectsANonPositiveBudget() {
        RecordingRepository repository = new RecordingRepository();
        Result result = run(repository, request(3, 3, 0L, Collections.singletonList(Mood.RELAXED)));

        assertFalse(repository.called);
        assertTrue(result.failure instanceof IllegalArgumentException);
    }

    @Test public void rejectsAnEmptyMoodSelection() {
        RecordingRepository repository = new RecordingRepository();
        Result result = run(repository, request(3, 3, 3_000_000L, Collections.emptyList()));

        assertFalse("the backend answers 400 for this, so it must be caught locally", repository.called);
        assertTrue(result.failure instanceof IllegalArgumentException);
    }

    @Test public void acceptsTheBoundaryValues() {
        RecordingRepository repository = new RecordingRepository();
        Result result = run(repository, request(7, 6, 1L, Collections.singletonList(Mood.BIZARRE)));

        assertTrue("days=7 and 6 places per day are still inside the range", repository.called);
        assertEquals("stub", result.success.getTitle());
    }
}
