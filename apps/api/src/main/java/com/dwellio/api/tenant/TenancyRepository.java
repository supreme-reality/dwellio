package com.dwellio.api.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenancyRepository extends JpaRepository<TenancyEntity, UUID> {

    List<TenancyEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
