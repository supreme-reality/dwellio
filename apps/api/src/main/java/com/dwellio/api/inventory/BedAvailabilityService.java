package com.dwellio.api.inventory;

import org.springframework.stereotype.Service;

@Service
public class BedAvailabilityService {

    public enum Availability {
        AVAILABLE,
        OCCUPIED,
        BLOCKED
    }

    private final OccupancyProbe occupancyProbe;

    public BedAvailabilityService(OccupancyProbe occupancyProbe) {
        this.occupancyProbe = occupancyProbe;
    }

    /**
     * Derived availability for catalog-ACTIVE beds exposed by inventory APIs.
     * Catalog-inactive beds are omitted from list/get; do not call resolve for them.
     */
    public Availability resolve(BedEntity bed) {
        if (bed.getBlockReason() != null && !bed.getBlockReason().isBlank()) {
            return Availability.BLOCKED;
        }
        if (occupancyProbe.hasActiveOccupancy(bed.getId())) {
            return Availability.OCCUPIED;
        }
        if (!"ACTIVE".equals(bed.getStatus())) {
            throw new IllegalStateException(
                    "Catalog-inactive beds are omitted from inventory APIs; no client availability value"
            );
        }
        return Availability.AVAILABLE;
    }

    public boolean isAssignable(BedEntity bed) {
        if (!"ACTIVE".equals(bed.getStatus())) {
            return false;
        }
        if (bed.getBlockReason() != null && !bed.getBlockReason().isBlank()) {
            return false;
        }
        return !occupancyProbe.hasActiveOccupancy(bed.getId());
    }
}
