package com.dwellio.api.ticket;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
public class TicketController {

    private final CurrentUserService currentUserService;
    private final TicketService ticketService;

    public TicketController(CurrentUserService currentUserService, TicketService ticketService) {
        this.currentUserService = currentUserService;
        this.ticketService = ticketService;
    }

    @GetMapping("/properties/{propertyId}/tickets")
    public List<TicketResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return ticketService.list(user, propertyId);
    }

    @PostMapping("/properties/{propertyId}/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateTicketRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return ticketService.create(user, propertyId, request);
    }

    @GetMapping("/tickets/{ticketId}")
    public TicketResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ticketId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return ticketService.get(user, ticketId);
    }

    @PatchMapping("/tickets/{ticketId}")
    public TicketResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ticketId,
            @Valid @RequestBody UpdateTicketRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return ticketService.update(user, ticketId, request);
    }
}
