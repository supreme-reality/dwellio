package com.dwellio.api.inventory;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Probe for active occupancy. Returns false until stay/occupancy schema exists (Task 14).
 */
@Component
public class OccupancyProbe {

    public boolean hasActiveOccupancy(UUID bedId) {
        return false;
    }
}
