package com.example.finalproject.domain.repository;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Place;

import java.util.List;

public interface CatalogRepository {
    /** Full catalog page for a type, optionally narrowed by a free-text query. */
    void load(String type, String query, RepositoryCallback<List<Place>> callback);

    /** Type-ahead suggestions for a partial query. */
    void suggest(String type, String query, RepositoryCallback<List<Place>> callback);
}
