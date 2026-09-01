package com.dwellio.api.service;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ServiceController {

    private final CurrentUserService currentUserService;
    private final ServiceCatalogService serviceCatalogService;
    private final ServiceEnrollmentService serviceEnrollmentService;

    public ServiceController(
            CurrentUserService currentUserService,
            ServiceCatalogService serviceCatalogService,
            ServiceEnrollmentService serviceEnrollmentService
    ) {
        this.currentUserService = currentUserService;
        this.serviceCatalogService = serviceCatalogService;
        this.serviceEnrollmentService = serviceEnrollmentService;
    }

    @GetMapping("/properties/{propertyId}/services")
    public List<ServiceResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return serviceCatalogService.list(user, propertyId);
    }

    @PostMapping("/properties/{propertyId}/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateServiceRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return serviceCatalogService.create(user, propertyId, request);
    }

    @PatchMapping("/services/{serviceId}")
    public ServiceResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateServiceRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return serviceCatalogService.update(user, serviceId, request);
    }

    @GetMapping("/services/{serviceId}/config")
    public List<ServiceConfigResponse> listConfig(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID serviceId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return serviceCatalogService.listConfig(user, serviceId);
    }

    @PostMapping("/services/{serviceId}/config")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceConfigResponse createConfig(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID serviceId,
            @Valid @RequestBody CreateServiceConfigRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return serviceCatalogService.createConfig(user, serviceId, request);
    }

    @GetMapping("/tenancies/{tenancyId}/services")
    public List<ServiceEnrollmentResponse> listEnrollments(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tenancyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return serviceEnrollmentService.listByTenancy(user, tenancyId);
    }

    @PostMapping("/tenancies/{tenancyId}/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceEnrollmentResponse enroll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tenancyId,
            @Valid @RequestBody EnrollServiceRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return serviceEnrollmentService.enroll(user, tenancyId, request);
    }

    @PostMapping("/service-enrollments/{enrollmentId}/end")
    public ServiceEnrollmentResponse end(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody EndEnrollmentRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return serviceEnrollmentService.end(user, enrollmentId, request);
    }

    @PostMapping("/service-enrollments/{enrollmentId}/charges")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceChargeResponse charge(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody CreateServiceChargeRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return serviceEnrollmentService.recordCharge(user, enrollmentId, request);
    }
}
