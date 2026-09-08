package com.dwellio.api.ticket;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.org.OrganizationAccessService;
import com.dwellio.api.org.OrganizationMembershipEntity;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.property.PropertyMembershipRepository;
import com.dwellio.api.user.AppUserEntity;
import com.dwellio.api.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketService {

    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");

    private final TicketRepository ticketRepository;
    private final PropertyAccessService propertyAccessService;
    private final OrganizationAccessService organizationAccessService;
    private final PropertyMembershipRepository propertyMembershipRepository;
    private final AppUserRepository appUserRepository;

    public TicketService(
            TicketRepository ticketRepository,
            PropertyAccessService propertyAccessService,
            OrganizationAccessService organizationAccessService,
            PropertyMembershipRepository propertyMembershipRepository,
            AppUserRepository appUserRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.propertyAccessService = propertyAccessService;
        this.organizationAccessService = organizationAccessService;
        this.propertyMembershipRepository = propertyMembershipRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> list(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        return ticketRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(AppUserEntity user, UUID ticketId) {
        TicketEntity entity = requireTicket(ticketId);
        propertyAccessService.requireReadableProperty(user, entity.getPropertyId());
        return toResponse(entity);
    }

    @Transactional
    public TicketResponse create(AppUserEntity user, UUID propertyId, CreateTicketRequest request) {
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, propertyId);
        UUID assignee = request.assignedToUserId();
        if (assignee != null) {
            requireAssignable(property, assignee);
        }
        String status = request.status() == null || request.status().isBlank()
                ? "OPEN"
                : request.status().trim().toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        TicketEntity entity = ticketRepository.save(new TicketEntity(
                UUID.randomUUID(),
                property.getId(),
                request.title().trim(),
                blankToNull(request.body()),
                status,
                user.getId(),
                assignee,
                now,
                now
        ));
        return toResponse(entity);
    }

    @Transactional
    public TicketResponse update(AppUserEntity user, UUID ticketId, UpdateTicketRequest request) {
        TicketEntity entity = requireTicket(ticketId);
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, entity.getPropertyId());

        if (request.title() != null && !request.title().isBlank()) {
            entity.setTitle(request.title().trim());
        }
        if (request.body() != null) {
            entity.setBody(blankToNull(request.body()));
        }
        if (request.status() != null && !request.status().isBlank()) {
            String status = request.status().trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid ticket status");
            }
            entity.setStatus(status);
        }
        if (Boolean.TRUE.equals(request.clearAssignee())) {
            entity.setAssignedToUserId(null);
        } else if (request.assignedToUserId() != null) {
            requireAssignable(property, request.assignedToUserId());
            entity.setAssignedToUserId(request.assignedToUserId());
        }
        entity.setUpdatedAt(Instant.now());
        return toResponse(entity);
    }

    @Transactional
    public void unassignOpenTicketsOnProperty(UUID userId, UUID propertyId) {
        ticketRepository.clearOpenAssigneesOnProperty(userId, propertyId, Instant.now());
    }

    private void requireAssignable(PropertyEntity property, UUID assigneeUserId) {
        if (!appUserRepository.existsById(assigneeUserId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Assignee user not found");
        }
        OrganizationMembershipEntity membership;
        try {
            membership = organizationAccessService.requireActiveMembership(property.getOrganizationId(), assigneeUserId);
        } catch (ApiException ex) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Assignee must be Owner or Manager on the property"
            );
        }
        if ("OWNER".equals(membership.getRole())) {
            return;
        }
        if (propertyMembershipRepository.existsActiveManagerAssignment(property.getId(), assigneeUserId)) {
            return;
        }
        throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Assignee must be Owner or Manager on the property"
        );
    }

    private TicketEntity requireTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private TicketResponse toResponse(TicketEntity entity) {
        return new TicketResponse(
                entity.getId(),
                entity.getPropertyId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getStatus(),
                entity.getCreatedByUserId(),
                entity.getAssignedToUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
