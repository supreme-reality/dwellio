package com.dwellio.api.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseTypeRepository extends JpaRepository<ExpenseTypeEntity, UUID> {

    List<ExpenseTypeEntity> findByPropertyIdOrderByCreatedAtAsc(UUID propertyId);

    boolean existsByPropertyIdAndNameIgnoreCase(UUID propertyId, String name);

    Optional<ExpenseTypeEntity> findByIdAndPropertyId(UUID id, UUID propertyId);
}
