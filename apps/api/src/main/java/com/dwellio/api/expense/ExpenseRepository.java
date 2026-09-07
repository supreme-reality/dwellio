package com.dwellio.api.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, UUID> {

    List<ExpenseEntity> findByPropertyIdOrderByIncurredOnDescCreatedAtDesc(UUID propertyId);

    boolean existsByExpenseTypeId(UUID expenseTypeId);
}
