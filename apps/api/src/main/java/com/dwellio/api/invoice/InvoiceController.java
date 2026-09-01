package com.dwellio.api.invoice;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InvoiceController {

    private final CurrentUserService currentUserService;
    private final InvoiceService invoiceService;

    public InvoiceController(CurrentUserService currentUserService, InvoiceService invoiceService) {
        this.currentUserService = currentUserService;
        this.invoiceService = invoiceService;
    }

    @GetMapping("/properties/{propertyId}/invoices")
    public List<InvoiceResponse> listByProperty(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return invoiceService.listByProperty(user, propertyId);
    }

    @GetMapping("/tenants/{tenantId}/invoices")
    public List<InvoiceResponse> listByTenant(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tenantId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return invoiceService.listByTenant(user, tenantId);
    }

    @GetMapping("/tenancies/{tenancyId}/invoices")
    public List<InvoiceResponse> listByTenancy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tenancyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return invoiceService.listByTenancy(user, tenancyId);
    }

    @GetMapping("/invoices/{invoiceId}")
    public InvoiceResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID invoiceId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return invoiceService.get(user, invoiceId);
    }
}
