package com.dwellio.api.expense;

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
import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseTypeRepository expenseTypeRepository;
    private final ExpenseRepository expenseRepository;
    private final PropertyAccessService propertyAccessService;

    public ExpenseService(
            ExpenseTypeRepository expenseTypeRepository,
            ExpenseRepository expenseRepository,
            PropertyAccessService propertyAccessService
    ) {
        this.expenseTypeRepository = expenseTypeRepository;
        this.expenseRepository = expenseRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional(readOnly = true)
    public List<ExpenseTypeResponse> listTypes(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        return expenseTypeRepository.findByPropertyIdOrderByCreatedAtAsc(propertyId).stream()
                .map(this::toTypeResponse)
                .toList();
    }

    @Transactional
    public ExpenseTypeResponse createType(AppUserEntity user, UUID propertyId, CreateExpenseTypeRequest request) {
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, propertyId);
        String name = request.name().trim();
        if (expenseTypeRepository.existsByPropertyIdAndNameIgnoreCase(property.getId(), name)) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Expense type name already exists");
        }
        Instant now = Instant.now();
        ExpenseTypeEntity entity = expenseTypeRepository.save(new ExpenseTypeEntity(
                UUID.randomUUID(),
                property.getId(),
                name,
                true,
                now,
                now
        ));
        return toTypeResponse(entity);
    }

    @Transactional
    public ExpenseTypeResponse updateType(AppUserEntity user, UUID expenseTypeId, UpdateExpenseTypeRequest request) {
        ExpenseTypeEntity entity = requireType(expenseTypeId);
        propertyAccessService.requireInventoryMutator(user, entity.getPropertyId());

        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(entity.getName())
                    && expenseTypeRepository.existsByPropertyIdAndNameIgnoreCase(entity.getPropertyId(), name)) {
                throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Expense type name already exists");
            }
            entity.setName(name);
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        entity.setUpdatedAt(Instant.now());
        return toTypeResponse(entity);
    }

    @Transactional
    public void deleteType(AppUserEntity user, UUID expenseTypeId) {
        ExpenseTypeEntity entity = requireType(expenseTypeId);
        propertyAccessService.requireInventoryMutator(user, entity.getPropertyId());
        if (expenseRepository.existsByExpenseTypeId(expenseTypeId)) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "Expense type has expenses; delete expenses first"
            );
        }
        expenseTypeRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listExpenses(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        return expenseRepository.findByPropertyIdOrderByIncurredOnDescCreatedAtDesc(propertyId).stream()
                .map(this::toExpenseResponse)
                .toList();
    }

    @Transactional
    public ExpenseResponse createExpense(AppUserEntity user, UUID propertyId, CreateExpenseRequest request) {
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, propertyId);
        ExpenseTypeEntity type = requireTypeOnProperty(request.expenseTypeId(), property.getId());
        Instant now = Instant.now();
        ExpenseEntity entity = expenseRepository.save(new ExpenseEntity(
                UUID.randomUUID(),
                property.getId(),
                type.getId(),
                money(request.amount()),
                property.getDefaultCurrency(),
                request.incurredOn(),
                blankToNull(request.notes()),
                user.getId(),
                now,
                now
        ));
        return toExpenseResponse(entity);
    }

    @Transactional
    public ExpenseResponse updateExpense(AppUserEntity user, UUID expenseId, UpdateExpenseRequest request) {
        ExpenseEntity entity = requireExpense(expenseId);
        propertyAccessService.requireInventoryMutator(user, entity.getPropertyId());

        if (request.expenseTypeId() != null) {
            ExpenseTypeEntity type = requireTypeOnProperty(request.expenseTypeId(), entity.getPropertyId());
            entity.setExpenseTypeId(type.getId());
        }
        if (request.amount() != null) {
            entity.setAmount(money(request.amount()));
        }
        if (request.incurredOn() != null) {
            entity.setIncurredOn(request.incurredOn());
        }
        if (request.notes() != null) {
            entity.setNotes(blankToNull(request.notes()));
        }
        entity.setUpdatedAt(Instant.now());
        return toExpenseResponse(entity);
    }

    @Transactional
    public void deleteExpense(AppUserEntity user, UUID expenseId) {
        ExpenseEntity entity = requireExpense(expenseId);
        propertyAccessService.requireInventoryMutator(user, entity.getPropertyId());
        expenseRepository.delete(entity);
    }

    private ExpenseTypeEntity requireType(UUID expenseTypeId) {
        return expenseTypeRepository.findById(expenseTypeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Expense type not found"));
    }

    private ExpenseTypeEntity requireTypeOnProperty(UUID expenseTypeId, UUID propertyId) {
        return expenseTypeRepository.findByIdAndPropertyId(expenseTypeId, propertyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Expense type not found"));
    }

    private ExpenseEntity requireExpense(UUID expenseId) {
        return expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Expense not found"));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ExpenseTypeResponse toTypeResponse(ExpenseTypeEntity entity) {
        return new ExpenseTypeResponse(
                entity.getId(),
                entity.getPropertyId(),
                entity.getName(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ExpenseResponse toExpenseResponse(ExpenseEntity entity) {
        return new ExpenseResponse(
                entity.getId(),
                entity.getPropertyId(),
                entity.getExpenseTypeId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getIncurredOn(),
                entity.getNotes(),
                entity.getCreatedByUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
