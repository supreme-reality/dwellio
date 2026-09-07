package com.dwellio.api.expense;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class ExpenseController {

    private final CurrentUserService currentUserService;
    private final ExpenseService expenseService;

    public ExpenseController(CurrentUserService currentUserService, ExpenseService expenseService) {
        this.currentUserService = currentUserService;
        this.expenseService = expenseService;
    }

    @GetMapping("/properties/{propertyId}/expense-types")
    public List<ExpenseTypeResponse> listTypes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return expenseService.listTypes(user, propertyId);
    }

    @PostMapping("/properties/{propertyId}/expense-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseTypeResponse createType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateExpenseTypeRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return expenseService.createType(user, propertyId, request);
    }

    @PatchMapping("/expense-types/{expenseTypeId}")
    public ExpenseTypeResponse updateType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID expenseTypeId,
            @Valid @RequestBody UpdateExpenseTypeRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return expenseService.updateType(user, expenseTypeId, request);
    }

    @DeleteMapping("/expense-types/{expenseTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID expenseTypeId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        expenseService.deleteType(user, expenseTypeId);
    }

    @GetMapping("/properties/{propertyId}/expenses")
    public List<ExpenseResponse> listExpenses(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return expenseService.listExpenses(user, propertyId);
    }

    @PostMapping("/properties/{propertyId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateExpenseRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return expenseService.createExpense(user, propertyId, request);
    }

    @PatchMapping("/expenses/{expenseId}")
    public ExpenseResponse updateExpense(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID expenseId,
            @Valid @RequestBody UpdateExpenseRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return expenseService.updateExpense(user, expenseId, request);
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID expenseId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        expenseService.deleteExpense(user, expenseId);
    }
}
