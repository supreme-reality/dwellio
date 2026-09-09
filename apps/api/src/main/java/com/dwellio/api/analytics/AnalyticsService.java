package com.dwellio.api.analytics;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.expense.ExpenseEntity;
import com.dwellio.api.expense.ExpenseRepository;
import com.dwellio.api.expense.ExpenseTypeEntity;
import com.dwellio.api.expense.ExpenseTypeRepository;
import com.dwellio.api.inventory.BedAvailabilityService;
import com.dwellio.api.inventory.BedEntity;
import com.dwellio.api.inventory.BedRepository;
import com.dwellio.api.invoice.InvoiceEntity;
import com.dwellio.api.invoice.InvoiceRepository;
import com.dwellio.api.payment.PaymentRepository;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.tenant.TenantEntity;
import com.dwellio.api.tenant.TenantRepository;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final PropertyAccessService propertyAccessService;
    private final BedRepository bedRepository;
    private final BedAvailabilityService bedAvailabilityService;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseTypeRepository expenseTypeRepository;
    private final TenantRepository tenantRepository;

    public AnalyticsService(
            PropertyAccessService propertyAccessService,
            BedRepository bedRepository,
            BedAvailabilityService bedAvailabilityService,
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            ExpenseRepository expenseRepository,
            ExpenseTypeRepository expenseTypeRepository,
            TenantRepository tenantRepository
    ) {
        this.propertyAccessService = propertyAccessService;
        this.bedRepository = bedRepository;
        this.bedAvailabilityService = bedAvailabilityService;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.expenseRepository = expenseRepository;
        this.expenseTypeRepository = expenseTypeRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public OccupancyAnalyticsResponse occupancy(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        List<BedEntity> beds = bedRepository.findActiveByPropertyId(propertyId);
        long total = beds.size();
        long occupied = 0;
        long blocked = 0;
        for (BedEntity bed : beds) {
            BedAvailabilityService.Availability availability = bedAvailabilityService.resolve(bed);
            if (availability == BedAvailabilityService.Availability.OCCUPIED) {
                occupied++;
            } else if (availability == BedAvailabilityService.Availability.BLOCKED) {
                blocked++;
            }
        }
        BigDecimal rate = total == 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(occupied)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        return new OccupancyAnalyticsResponse(total, occupied, blocked, rate);
    }

    @Transactional(readOnly = true)
    public RevenueAnalyticsResponse revenue(AppUserEntity user, UUID propertyId, LocalDate from, LocalDate to) {
        PropertyEntity property = propertyAccessService.requireReadableProperty(user, propertyId);
        DateRange range = resolveRange(from, to);
        Instant fromInstant = range.from().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = range.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal paid = paymentRepository.sumConfirmedAmountForProperty(propertyId, fromInstant, toExclusive);
        BigDecimal invoices = invoiceRepository.sumFinalizedTotalForProperty(propertyId, range.from(), range.to());
        return new RevenueAnalyticsResponse(
                property.getDefaultCurrency(),
                money(paid),
                money(invoices)
        );
    }

    @Transactional(readOnly = true)
    public ExpenseAnalyticsResponse expenses(AppUserEntity user, UUID propertyId, LocalDate from, LocalDate to) {
        PropertyEntity property = propertyAccessService.requireReadableProperty(user, propertyId);
        DateRange range = resolveRange(from, to);
        BigDecimal total = expenseRepository.sumAmountForProperty(propertyId, range.from(), range.to());
        List<ExpenseTypeTotalResponse> byType = new ArrayList<>();
        for (Object[] row : expenseRepository.sumByTypeForProperty(propertyId, range.from(), range.to())) {
            byType.add(new ExpenseTypeTotalResponse(
                    (UUID) row[0],
                    (String) row[1],
                    money((BigDecimal) row[2])
            ));
        }
        return new ExpenseAnalyticsResponse(property.getDefaultCurrency(), money(total), byType);
    }

    @Transactional(readOnly = true)
    public String exportCsv(AppUserEntity user, UUID propertyId, String exportType, LocalDate from, LocalDate to) {
        PropertyEntity property = propertyAccessService.requireReadableProperty(user, propertyId);
        DateRange range = resolveRange(from, to);
        String type = exportType == null ? "" : exportType.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "tenants" -> exportTenants(property);
            case "expenses" -> exportExpenses(propertyId, range);
            case "invoices" -> exportInvoices(propertyId, range);
            default -> throw new ApiException(
                    ErrorCode.NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "Unknown export type"
            );
        };
    }

    private String exportTenants(PropertyEntity property) {
        StringBuilder csv = new StringBuilder("id,firstName,lastName,phone,email,status\n");
        for (TenantEntity tenant : tenantRepository.findByOrganizationIdOrderByCreatedAtAsc(property.getOrganizationId())) {
            csv.append(csvEscape(tenant.getId().toString())).append(',')
                    .append(csvEscape(tenant.getFirstName())).append(',')
                    .append(csvEscape(nullToEmpty(tenant.getLastName()))).append(',')
                    .append(csvEscape(tenant.getPhone())).append(',')
                    .append(csvEscape(nullToEmpty(tenant.getEmail()))).append(',')
                    .append(csvEscape(tenant.getStatus())).append('\n');
        }
        return csv.toString();
    }

    private String exportExpenses(UUID propertyId, DateRange range) {
        Map<UUID, ExpenseTypeEntity> types = expenseTypeRepository.findByPropertyIdOrderByCreatedAtAsc(propertyId)
                .stream()
                .collect(Collectors.toMap(ExpenseTypeEntity::getId, Function.identity()));
        StringBuilder csv = new StringBuilder("id,expenseType,amount,currency,incurredOn,notes\n");
        for (ExpenseEntity expense : expenseRepository.findByPropertyIdAndIncurredOnBetweenOrderByIncurredOnAsc(
                propertyId, range.from(), range.to())) {
            ExpenseTypeEntity type = types.get(expense.getExpenseTypeId());
            String typeName = type == null ? expense.getExpenseTypeId().toString() : type.getName();
            csv.append(csvEscape(expense.getId().toString())).append(',')
                    .append(csvEscape(typeName)).append(',')
                    .append(expense.getAmount()).append(',')
                    .append(csvEscape(expense.getCurrency())).append(',')
                    .append(expense.getIncurredOn()).append(',')
                    .append(csvEscape(nullToEmpty(expense.getNotes()))).append('\n');
        }
        return csv.toString();
    }

    private String exportInvoices(UUID propertyId, DateRange range) {
        StringBuilder csv = new StringBuilder("id,tenancyId,invoiceType,billingDate,status,currency,total\n");
        for (InvoiceEntity invoice : invoiceRepository.findByPropertyIdAndBillingDateBetween(
                propertyId, range.from(), range.to())) {
            csv.append(csvEscape(invoice.getId().toString())).append(',')
                    .append(csvEscape(invoice.getTenancyId().toString())).append(',')
                    .append(csvEscape(invoice.getInvoiceType())).append(',')
                    .append(invoice.getBillingDate()).append(',')
                    .append(csvEscape(invoice.getStatus())).append(',')
                    .append(csvEscape(invoice.getCurrency())).append(',')
                    .append(invoice.getTotal()).append('\n');
        }
        return csv.toString();
    }

    private static DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate resolvedFrom = from;
        LocalDate resolvedTo = to;
        if (resolvedFrom == null && resolvedTo == null) {
            YearMonth month = YearMonth.now(ZoneOffset.UTC);
            resolvedFrom = month.atDay(1);
            resolvedTo = month.atEndOfMonth();
        } else if (resolvedFrom == null) {
            resolvedFrom = resolvedTo.withDayOfMonth(1);
        } else if (resolvedTo == null) {
            resolvedTo = YearMonth.from(resolvedFrom).atEndOfMonth();
        }
        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "to must be on or after from");
        }
        return new DateRange(resolvedFrom, resolvedTo);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
