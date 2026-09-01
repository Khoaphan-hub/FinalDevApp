package com.example.finalproject.domain.repository;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.DeviceLocation;

/**
 * Reads the current device position. The caller is responsible for holding the runtime
 * permission before calling; an implementation reports a missing permission through onError.
 */
public interface LocationRepository {
    void currentLocation(RepositoryCallback<DeviceLocation> callback);

    /** Stops any pending location listener and background work. Call from onDestroy. */
    void release();
}
