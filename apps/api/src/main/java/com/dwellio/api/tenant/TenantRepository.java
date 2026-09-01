package com.dwellio.api.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    List<TenantEntity> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    boolean existsByOrganizationIdAndPhone(UUID organizationId, String phone);

    boolean existsByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    boolean existsByOrganizationIdAndPhoneAndIdNot(UUID organizationId, String phone, UUID id);

    boolean existsByOrganizationIdAndEmailIgnoreCaseAndIdNot(UUID organizationId, String email, UUID id);

    Optional<TenantEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
