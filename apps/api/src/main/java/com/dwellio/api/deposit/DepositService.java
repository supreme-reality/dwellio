package com.dwellio.api.deposit;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.tenant.TenancyEntity;
import com.dwellio.api.tenant.TenancyRepository;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class DepositService {

    private final DepositLedgerRepository depositLedgerRepository;
    private final TenancyRepository tenancyRepository;
    private final PropertyAccessService propertyAccessService;

    public DepositService(
            DepositLedgerRepository depositLedgerRepository,
            TenancyRepository tenancyRepository,
            PropertyAccessService propertyAccessService
    ) {
        this.depositLedgerRepository = depositLedgerRepository;
        this.tenancyRepository = tenancyRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional(readOnly = true)
    public DepositSummaryResponse getSummary(AppUserEntity user, UUID tenancyId) {
        TenancyEntity tenancy = requireReadableTenancy(user, tenancyId);
        PropertyEntity property = propertyAccessService.requireActiveProperty(tenancy.getPropertyId());
        List<DepositLedgerEntity> entries = depositLedgerRepository.findByTenancyIdOrderByCreatedAtAsc(tenancyId);
        Totals totals = computeTotals(entries);
        return new DepositSummaryResponse(
                tenancyId,
                totals.balance(),
                totals.receipts(),
                totals.deductions(),
                totals.refunds(),
                property.getDefaultCurrency()
        );
    }

    @Transactional(readOnly = true)
    public DepositLedgerResponse getLedger(AppUserEntity user, UUID tenancyId) {
        requireReadableTenancy(user, tenancyId);
        List<DepositLedgerEntity> entries = depositLedgerRepository.findByTenancyIdOrderByCreatedAtAsc(tenancyId);
        Totals totals = computeTotals(entries);
        List<DepositLedgerResponse.DepositLedgerEntryResponse> items = entries.stream()
                .map(e -> new DepositLedgerResponse.DepositLedgerEntryResponse(
                        e.getId(),
                        e.getType(),
                        e.getAmount(),
                        e.getReference(),
                        e.getNotes(),
                        e.getCreatedAt()
                ))
                .toList();
        return new DepositLedgerResponse(tenancyId, totals.balance(), items);
    }

    private TenancyEntity requireReadableTenancy(AppUserEntity user, UUID tenancyId) {
        TenancyEntity tenancy = tenancyRepository.findById(tenancyId)
                .orElseThrow(this::notFound);
        try {
            propertyAccessService.requireReadableProperty(user, tenancy.getPropertyId());
        } catch (ApiException ex) {
            throw notFound();
        }
        return tenancy;
    }

    private static Totals computeTotals(List<DepositLedgerEntity> entries) {
        BigDecimal receipts = zero();
        BigDecimal deductions = zero();
        BigDecimal refunds = zero();
        for (DepositLedgerEntity entry : entries) {
            switch (entry.getType()) {
                case "RECEIPT" -> receipts = receipts.add(entry.getAmount());
                case "DEDUCTION" -> deductions = deductions.add(entry.getAmount());
                case "REFUND" -> refunds = refunds.add(entry.getAmount());
                default -> throw new IllegalStateException("Unexpected deposit ledger type: " + entry.getType());
            }
        }
        BigDecimal balance = receipts.subtract(deductions).subtract(refunds);
        return new Totals(balance, receipts, deductions, refunds);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }

    private ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenancy not found");
    }

    private record Totals(
            BigDecimal balance,
            BigDecimal receipts,
            BigDecimal deductions,
            BigDecimal refunds
    ) {
    }
}
