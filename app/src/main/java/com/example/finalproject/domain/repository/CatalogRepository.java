package com.example.finalproject.domain.repository;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Place;

import java.util.List;

public interface CatalogRepository {
    void load(String type, String query, RepositoryCallback<List<Place>> callback);
}
