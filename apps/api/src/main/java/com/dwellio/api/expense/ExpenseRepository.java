package com.dwellio.api.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, UUID> {

    List<ExpenseEntity> findByPropertyIdOrderByIncurredOnDescCreatedAtDesc(UUID propertyId);

    boolean existsByExpenseTypeId(UUID expenseTypeId);

    @Query("""
            select coalesce(sum(e.amount), 0)
            from ExpenseEntity e
            where e.propertyId = :propertyId
              and e.incurredOn >= :fromInclusive
              and e.incurredOn <= :toInclusive
            """)
    BigDecimal sumAmountForProperty(
            @Param("propertyId") UUID propertyId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive
    );

    @Query("""
            select e.expenseTypeId, t.name, coalesce(sum(e.amount), 0)
            from ExpenseEntity e
            join ExpenseTypeEntity t on t.id = e.expenseTypeId
            where e.propertyId = :propertyId
              and e.incurredOn >= :fromInclusive
              and e.incurredOn <= :toInclusive
            group by e.expenseTypeId, t.name
            order by t.name asc
            """)
    List<Object[]> sumByTypeForProperty(
            @Param("propertyId") UUID propertyId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive
    );

    List<ExpenseEntity> findByPropertyIdAndIncurredOnBetweenOrderByIncurredOnAsc(
            UUID propertyId,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );
}
