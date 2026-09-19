package com.dwellio.api.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceCatalogRepository extends JpaRepository<ServiceEntity, UUID> {

    List<ServiceEntity> findByPropertyIdOrderByCreatedAtAsc(UUID propertyId);

    boolean existsByPropertyIdAndNameIgnoreCase(UUID propertyId, String name);
}
