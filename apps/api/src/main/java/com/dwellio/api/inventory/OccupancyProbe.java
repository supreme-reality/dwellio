package com.dwellio.api.inventory;

import com.dwellio.api.tenant.OccupancyRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OccupancyProbe {

    private final OccupancyRepository occupancyRepository;

    public OccupancyProbe(OccupancyRepository occupancyRepository) {
        this.occupancyRepository = occupancyRepository;
    }

    public boolean hasActiveOccupancy(UUID bedId) {
        return occupancyRepository.existsByBedIdAndStatus(bedId, "ACTIVE");
    }
}
