package com.dwellio.api.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedAvailabilityServiceTest {

    @Mock
    OccupancyProbe occupancyProbe;

    BedAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new BedAvailabilityService(occupancyProbe);
    }

    @Test
    void blockedWhenBlockReasonSetEvenIfOccupied() {
        BedEntity bed = activeBed();
        bed.setBlockReason("Maintenance");
        // Occupancy stub intentionally unused: BLOCKED short-circuits before occupancy check.

        assertThat(service.resolve(bed)).isEqualTo(BedAvailabilityService.Availability.BLOCKED);
    }

    @Test
    void occupiedWhenActiveOccupancyAndNotBlocked() {
        BedEntity bed = activeBed();
        when(occupancyProbe.hasActiveOccupancy(bed.getId())).thenReturn(true);

        assertThat(service.resolve(bed)).isEqualTo(BedAvailabilityService.Availability.OCCUPIED);
    }

    @Test
    void availableWhenActiveAndFree() {
        BedEntity bed = activeBed();
        when(occupancyProbe.hasActiveOccupancy(bed.getId())).thenReturn(false);

        assertThat(service.resolve(bed)).isEqualTo(BedAvailabilityService.Availability.AVAILABLE);
    }

    @Test
    void blankBlockReasonDoesNotBlock() {
        BedEntity bed = activeBed();
        bed.setBlockReason("   ");
        when(occupancyProbe.hasActiveOccupancy(bed.getId())).thenReturn(false);

        assertThat(service.resolve(bed)).isEqualTo(BedAvailabilityService.Availability.AVAILABLE);
    }

    @Test
    void catalogInactiveWithoutOccupancyIsNotAssignableAndNotOccupied() {
        Instant now = Instant.now();
        BedEntity bed = new BedEntity(UUID.randomUUID(), UUID.randomUUID(), "Bed A", "INACTIVE", now, now);
        when(occupancyProbe.hasActiveOccupancy(bed.getId())).thenReturn(false);

        assertThat(service.isAssignable(bed)).isFalse();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.resolve(bed)
        );
    }

    @Test
    void isAssignableWhenActiveAndFree() {
        BedEntity bed = activeBed();
        when(occupancyProbe.hasActiveOccupancy(bed.getId())).thenReturn(false);

        assertThat(service.isAssignable(bed)).isTrue();
    }

    private static BedEntity activeBed() {
        Instant now = Instant.now();
        return new BedEntity(UUID.randomUUID(), UUID.randomUUID(), "Bed A", "ACTIVE", now, now);
    }
}
