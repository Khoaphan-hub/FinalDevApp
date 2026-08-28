package com.example.finalproject.domain.callback;

public interface RepositoryCallback<T> {
    void onSuccess(T result);
    void onError(Exception e);
}
