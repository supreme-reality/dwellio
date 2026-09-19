package com.dwellio.api.service;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ServiceCatalogService {

    private final ServiceCatalogRepository serviceCatalogRepository;
    private final ServiceConfigRepository serviceConfigRepository;
    private final PropertyAccessService propertyAccessService;

    public ServiceCatalogService(
            ServiceCatalogRepository serviceCatalogRepository,
            ServiceConfigRepository serviceConfigRepository,
            PropertyAccessService propertyAccessService
    ) {
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.serviceConfigRepository = serviceConfigRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        return serviceCatalogRepository.findByPropertyIdOrderByCreatedAtAsc(propertyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ServiceResponse create(AppUserEntity user, UUID propertyId, CreateServiceRequest request) {
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, propertyId);
        String name = request.name().trim();
        if (serviceCatalogRepository.existsByPropertyIdAndNameIgnoreCase(property.getId(), name)) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Service name already exists");
        }
        Instant now = Instant.now();
        ServiceEntity entity = serviceCatalogRepository.save(new ServiceEntity(
                UUID.randomUUID(),
                property.getId(),
                name,
                request.billingType().trim().toUpperCase(Locale.ROOT),
                request.billingTiming().trim().toUpperCase(Locale.ROOT),
                request.mandatory(),
                request.prorationSetting().trim(),
                "ACTIVE",
                now,
                now
        ));
        return toResponse(entity);
    }

    @Transactional
    public ServiceResponse update(AppUserEntity user, UUID serviceId, UpdateServiceRequest request) {
        ServiceEntity entity = requireService(serviceId);
        propertyAccessService.requireInventoryMutator(user, entity.getPropertyId());

        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(entity.getName())
                    && serviceCatalogRepository.existsByPropertyIdAndNameIgnoreCase(entity.getPropertyId(), name)) {
                throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Service name already exists");
            }
            entity.setName(name);
        }
        if (request.billingType() != null) {
            entity.setBillingType(request.billingType().trim().toUpperCase(Locale.ROOT));
        }
        if (request.billingTiming() != null) {
            entity.setBillingTiming(request.billingTiming().trim().toUpperCase(Locale.ROOT));
        }
        if (request.mandatory() != null) {
            entity.setMandatory(request.mandatory());
        }
        if (request.prorationSetting() != null && !request.prorationSetting().isBlank()) {
            entity.setProrationSetting(request.prorationSetting().trim());
        }
        if (request.status() != null) {
            entity.setStatus(request.status().trim().toUpperCase(Locale.ROOT));
        }
        entity.setUpdatedAt(Instant.now());
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ServiceConfigResponse> listConfig(AppUserEntity user, UUID serviceId) {
        ServiceEntity service = requireService(serviceId);
        propertyAccessService.requireReadableProperty(user, service.getPropertyId());
        return serviceConfigRepository.findByServiceIdOrderByEffectiveFromDesc(serviceId).stream()
                .map(this::toConfigResponse)
                .toList();
    }

    @Transactional
    public ServiceConfigResponse createConfig(AppUserEntity user, UUID serviceId, CreateServiceConfigRequest request) {
        ServiceEntity service = requireService(serviceId);
        propertyAccessService.requireInventoryMutator(user, service.getPropertyId());

        LocalDate effectiveFrom = request.effectiveFrom();
        BigDecimal amount = scaleMoney(request.amount());

        List<ServiceConfigEntity> open = serviceConfigRepository.findByServiceIdOrderByEffectiveFromDesc(serviceId)
                .stream()
                .filter(c -> c.getEffectiveTo() == null)
                .sorted(Comparator.comparing(ServiceConfigEntity::getEffectiveFrom).reversed())
                .toList();
        for (ServiceConfigEntity existingOpen : open) {
            if (!effectiveFrom.isAfter(existingOpen.getEffectiveFrom())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "effectiveFrom must be after the current open config start"
                );
            }
            existingOpen.setEffectiveTo(effectiveFrom);
            existingOpen.setUpdatedAt(Instant.now());
        }

        List<ServiceConfigEntity> overlaps = serviceConfigRepository.findOverlapping(
                serviceId,
                effectiveFrom,
                LocalDate.of(9999, 12, 31)
        );
        if (!overlaps.isEmpty() && overlaps.stream().anyMatch(c -> c.getEffectiveTo() != null)) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Service config date range overlaps existing history"
            );
        }

        Instant now = Instant.now();
        ServiceConfigEntity created = serviceConfigRepository.save(new ServiceConfigEntity(
                UUID.randomUUID(),
                serviceId,
                amount,
                effectiveFrom,
                null,
                now,
                now
        ));
        return toConfigResponse(created);
    }

    ServiceEntity requireService(UUID serviceId) {
        return serviceCatalogRepository.findById(serviceId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Service not found"));
    }

    private static BigDecimal scaleMoney(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Amount must have at most 2 decimal places"
            );
        }
    }

    private ServiceResponse toResponse(ServiceEntity entity) {
        return new ServiceResponse(
                entity.getId(),
                entity.getPropertyId(),
                entity.getName(),
                entity.getBillingType(),
                entity.getBillingTiming(),
                entity.isMandatory(),
                entity.getProrationSetting(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ServiceConfigResponse toConfigResponse(ServiceConfigEntity entity) {
        return new ServiceConfigResponse(
                entity.getId(),
                entity.getServiceId(),
                entity.getAmount(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
