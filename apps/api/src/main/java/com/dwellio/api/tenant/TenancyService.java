package com.dwellio.api.tenant;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenancyService {

    private final TenancyRepository tenancyRepository;
    private final OccupancyRepository occupancyRepository;
    private final PropertyAccessService propertyAccessService;

    public TenancyService(
            TenancyRepository tenancyRepository,
            OccupancyRepository occupancyRepository,
            PropertyAccessService propertyAccessService
    ) {
        this.tenancyRepository = tenancyRepository;
        this.occupancyRepository = occupancyRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional(readOnly = true)
    public TenancyStayContextResponse getStayContext(AppUserEntity user, UUID tenancyId) {
        TenancyEntity tenancy = tenancyRepository.findById(tenancyId)
                .orElseThrow(this::notFound);

        try {
            propertyAccessService.requireReadableProperty(user, tenancy.getPropertyId());
        } catch (ApiException ex) {
            // Scope denials must not leak existence via 403.
            throw notFound();
        }

        TenancyStayContextResponse.CurrentOccupancySummary occupancySummary = occupancyRepository
                .findByTenancyIdAndStatus(tenancy.getId(), "ACTIVE")
                .map(o -> new TenancyStayContextResponse.CurrentOccupancySummary(
                        o.getId(),
                        o.getBedId(),
                        o.getStatus(),
                        o.getStartedAt()
                ))
                .orElse(null);

        return new TenancyStayContextResponse(
                tenancy.getId(),
                tenancy.getTenantId(),
                tenancy.getPropertyId(),
                tenancy.getStatus(),
                occupancySummary,
                tenancy.getCreatedAt(),
                tenancy.getUpdatedAt()
        );
    }

    private ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found");
    }
}
