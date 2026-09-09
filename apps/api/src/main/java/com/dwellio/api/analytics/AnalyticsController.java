package com.dwellio.api.analytics;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}")
public class AnalyticsController {

    private final CurrentUserService currentUserService;
    private final AnalyticsService analyticsService;

    public AnalyticsController(CurrentUserService currentUserService, AnalyticsService analyticsService) {
        this.currentUserService = currentUserService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics/occupancy")
    public OccupancyAnalyticsResponse occupancy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return analyticsService.occupancy(user, propertyId);
    }

    @GetMapping("/analytics/revenue")
    public RevenueAnalyticsResponse revenue(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return analyticsService.revenue(user, propertyId, from, to);
    }

    @GetMapping("/analytics/expenses")
    public ExpenseAnalyticsResponse expenses(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return analyticsService.expenses(user, propertyId, from, to);
    }

    @GetMapping("/exports/{exportType}")
    public ResponseEntity<String> export(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @PathVariable String exportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        String csv = analyticsService.exportCsv(user, propertyId, exportType, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportType + ".csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }
}
