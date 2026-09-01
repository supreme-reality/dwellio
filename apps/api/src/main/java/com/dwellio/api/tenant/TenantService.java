package com.dwellio.api.tenant;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenancyRepository tenancyRepository;
    private final PropertyAccessService propertyAccessService;

    public TenantService(
            TenantRepository tenantRepository,
            TenancyRepository tenancyRepository,
            PropertyAccessService propertyAccessService
    ) {
        this.tenantRepository = tenantRepository;
        this.tenancyRepository = tenancyRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional
    public TenantResponse create(AppUserEntity user, UUID propertyId, CreateTenantRequest request) {
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, propertyId);
        String phone = request.phone().trim();
        String email = normalizeEmail(request.email());

        assertPhoneAvailable(property.getOrganizationId(), phone, null);
        assertEmailAvailable(property.getOrganizationId(), email, null);

        Instant now = Instant.now();
        TenantEntity tenant = tenantRepository.save(new TenantEntity(
                UUID.randomUUID(),
                property.getOrganizationId(),
                request.firstName().trim(),
                trimToNull(request.lastName()),
                phone,
                email,
                request.dateOfBirth(),
                trimToNull(request.gender()),
                trimToNull(request.address()),
                trimToNull(request.emergencyContactName()),
                trimToNull(request.emergencyContactPhone()),
                trimToNull(request.governmentId()),
                trimToNull(request.notes()),
                "ACTIVE",
                now,
                now
        ));
        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listByProperty(AppUserEntity user, UUID propertyId) {
        PropertyEntity property = propertyAccessService.requireReadableProperty(user, propertyId);
        return tenantRepository.findByOrganizationIdOrderByCreatedAtAsc(property.getOrganizationId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse get(AppUserEntity user, UUID tenantId) {
        return toResponse(requireReadableTenant(user, tenantId));
    }

    @Transactional
    public TenantResponse update(AppUserEntity user, UUID tenantId, UpdateTenantRequest request) {
        TenantEntity tenant = requireMutableTenant(user, tenantId);

        if (request.firstName() != null) {
            tenant.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            tenant.setLastName(trimToNull(request.lastName()));
        }
        if (request.phone() != null) {
            String phone = request.phone().trim();
            assertPhoneAvailable(tenant.getOrganizationId(), phone, tenant.getId());
            tenant.setPhone(phone);
        }
        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            assertEmailAvailable(tenant.getOrganizationId(), email, tenant.getId());
            tenant.setEmail(email);
        }
        if (request.dateOfBirth() != null) {
            tenant.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null) {
            tenant.setGender(trimToNull(request.gender()));
        }
        if (request.address() != null) {
            tenant.setAddress(trimToNull(request.address()));
        }
        if (request.emergencyContactName() != null) {
            tenant.setEmergencyContactName(trimToNull(request.emergencyContactName()));
        }
        if (request.emergencyContactPhone() != null) {
            tenant.setEmergencyContactPhone(trimToNull(request.emergencyContactPhone()));
        }
        if (request.governmentId() != null) {
            tenant.setGovernmentId(trimToNull(request.governmentId()));
        }
        if (request.notes() != null) {
            tenant.setNotes(trimToNull(request.notes()));
        }
        if (request.status() != null) {
            tenant.setStatus(request.status());
        }

        tenant.setUpdatedAt(Instant.now());
        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public List<StayHistoryItemResponse> stayHistory(AppUserEntity user, UUID tenantId) {
        TenantEntity tenant = requireReadableTenant(user, tenantId);
        return tenancyRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()).stream()
                .map(t -> new StayHistoryItemResponse(
                        t.getId(),
                        t.getPropertyId(),
                        t.getStatus(),
                        t.getCreatedAt()
                ))
                .toList();
    }

    private TenantEntity requireReadableTenant(AppUserEntity user, UUID tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(this::notFound);
        if (!propertyAccessService.canReadAnyPropertyInOrg(user, tenant.getOrganizationId())) {
            throw notFound();
        }
        return tenant;
    }

    private TenantEntity requireMutableTenant(AppUserEntity user, UUID tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(this::notFound);
        if (!propertyAccessService.isOrgMember(user, tenant.getOrganizationId())) {
            throw notFound();
        }
        if (!propertyAccessService.canMutateAnyPropertyInOrg(user, tenant.getOrganizationId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Property access required");
        }
        return tenant;
    }

    private void assertPhoneAvailable(UUID organizationId, String phone, UUID excludeId) {
        boolean taken = excludeId == null
                ? tenantRepository.existsByOrganizationIdAndPhone(organizationId, phone)
                : tenantRepository.existsByOrganizationIdAndPhoneAndIdNot(organizationId, phone, excludeId);
        if (taken) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Phone already exists in organization");
        }
    }

    private void assertEmailAvailable(UUID organizationId, String email, UUID excludeId) {
        if (email == null) {
            return;
        }
        boolean taken = excludeId == null
                ? tenantRepository.existsByOrganizationIdAndEmailIgnoreCase(organizationId, email)
                : tenantRepository.existsByOrganizationIdAndEmailIgnoreCaseAndIdNot(organizationId, email, excludeId);
        if (taken) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Email already exists in organization");
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenant not found");
    }

    private TenantResponse toResponse(TenantEntity tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getOrganizationId(),
                tenant.getFirstName(),
                tenant.getLastName(),
                tenant.getPhone(),
                tenant.getEmail(),
                tenant.getDateOfBirth(),
                tenant.getGender(),
                tenant.getAddress(),
                tenant.getEmergencyContactName(),
                tenant.getEmergencyContactPhone(),
                tenant.getGovernmentId(),
                tenant.getNotes(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
