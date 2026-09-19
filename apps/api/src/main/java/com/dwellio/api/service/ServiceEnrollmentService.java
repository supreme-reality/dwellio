package com.dwellio.api.service;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.tenant.TenancyEntity;
import com.dwellio.api.tenant.TenancyRepository;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceEnrollmentService {

    private final ServiceEnrollmentRepository enrollmentRepository;
    private final ServiceChargeRepository chargeRepository;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final TenancyRepository tenancyRepository;
    private final PropertyAccessService propertyAccessService;

    public ServiceEnrollmentService(
            ServiceEnrollmentRepository enrollmentRepository,
            ServiceChargeRepository chargeRepository,
            ServiceCatalogRepository serviceCatalogRepository,
            TenancyRepository tenancyRepository,
            PropertyAccessService propertyAccessService
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.chargeRepository = chargeRepository;
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.tenancyRepository = tenancyRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional(readOnly = true)
    public List<ServiceEnrollmentResponse> listByTenancy(AppUserEntity user, UUID tenancyId) {
        requireReadableTenancy(user, tenancyId);
        return enrollmentRepository.findByTenancyIdOrderByCreatedAtDesc(tenancyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ServiceEnrollmentResponse enroll(AppUserEntity user, UUID tenancyId, EnrollServiceRequest request) {
        TenancyEntity tenancy = requireMutableTenancy(user, tenancyId);
        ServiceEntity service = serviceCatalogRepository.findById(request.serviceId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Service not found"));
        if (!service.getPropertyId().equals(tenancy.getPropertyId())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Service does not belong to tenancy property"
            );
        }
        if (!"ACTIVE".equals(service.getStatus())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Service is not active"
            );
        }
        enrollmentRepository.findByTenancyIdAndServiceIdAndStatus(tenancyId, service.getId(), "ACTIVE")
                .ifPresent(existing -> {
                    throw new ApiException(
                            ErrorCode.CONFLICT,
                            HttpStatus.CONFLICT,
                            "Active enrollment already exists; end it before restarting"
                    );
                });

        Instant now = Instant.now();
        ServiceEnrollmentEntity enrollment = enrollmentRepository.save(new ServiceEnrollmentEntity(
                UUID.randomUUID(),
                tenancyId,
                service.getId(),
                "ACTIVE",
                request.startedAt(),
                null,
                now,
                now
        ));
        return toResponse(enrollment);
    }

    /**
     * Internal activation used by move-in payment façade.
     */
    @Transactional
    public ServiceEnrollmentEntity activateForMoveIn(UUID tenancyId, UUID serviceId, LocalDate startedAt) {
        return enrollmentRepository.findByTenancyIdAndServiceIdAndStatus(tenancyId, serviceId, "ACTIVE")
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    return enrollmentRepository.save(new ServiceEnrollmentEntity(
                            UUID.randomUUID(),
                            tenancyId,
                            serviceId,
                            "ACTIVE",
                            startedAt,
                            null,
                            now,
                            now
                    ));
                });
    }

    @Transactional
    public ServiceEnrollmentResponse end(AppUserEntity user, UUID enrollmentId, EndEnrollmentRequest request) {
        ServiceEnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Enrollment not found"));
        requireMutableTenancy(user, enrollment.getTenancyId());
        if (!"ACTIVE".equals(enrollment.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Enrollment is already ended");
        }
        if (request.endedAt().isBefore(enrollment.getStartedAt())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "endedAt cannot be before startedAt"
            );
        }
        Instant now = Instant.now();
        enrollment.setStatus("ENDED");
        enrollment.setEndedAt(request.endedAt());
        enrollment.setUpdatedAt(now);
        return toResponse(enrollment);
    }

    @Transactional
    public ServiceChargeResponse recordCharge(AppUserEntity user, UUID enrollmentId, CreateServiceChargeRequest request) {
        ServiceEnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Enrollment not found"));
        requireMutableTenancy(user, enrollment.getTenancyId());

        ServiceEntity service = serviceCatalogRepository.findById(enrollment.getServiceId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Service not found"));
        if (!"VARIABLE".equals(service.getBillingType())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Charges apply only to VARIABLE services"
            );
        }

        LocalDate billingPeriod = request.billingPeriod().withDayOfMonth(1);
        BigDecimal amount = scaleMoney(request.amount());
        Instant now = Instant.now();

        ServiceChargeEntity charge = chargeRepository
                .findByServiceEnrollmentIdAndBillingPeriod(enrollmentId, billingPeriod)
                .map(existing -> {
                    existing.setAmount(amount);
                    existing.setNote(blankToNull(request.note()));
                    existing.setUpdatedAt(now);
                    return existing;
                })
                .orElseGet(() -> chargeRepository.save(new ServiceChargeEntity(
                        UUID.randomUUID(),
                        enrollmentId,
                        billingPeriod,
                        amount,
                        blankToNull(request.note()),
                        now,
                        now
                )));
        return toChargeResponse(charge);
    }

    private TenancyEntity requireReadableTenancy(AppUserEntity user, UUID tenancyId) {
        TenancyEntity tenancy = tenancyRepository.findById(tenancyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found"));
        try {
            propertyAccessService.requireReadableProperty(user, tenancy.getPropertyId());
        } catch (ApiException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found");
        }
        return tenancy;
    }

    private TenancyEntity requireMutableTenancy(AppUserEntity user, UUID tenancyId) {
        TenancyEntity tenancy = tenancyRepository.findById(tenancyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found"));
        try {
            propertyAccessService.requireInventoryMutator(user, tenancy.getPropertyId());
        } catch (ApiException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found");
        }
        return tenancy;
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

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ServiceEnrollmentResponse toResponse(ServiceEnrollmentEntity entity) {
        return new ServiceEnrollmentResponse(
                entity.getId(),
                entity.getTenancyId(),
                entity.getServiceId(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ServiceChargeResponse toChargeResponse(ServiceChargeEntity entity) {
        return new ServiceChargeResponse(
                entity.getId(),
                entity.getServiceEnrollmentId(),
                entity.getBillingPeriod(),
                entity.getAmount(),
                entity.getNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
