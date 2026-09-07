package com.dwellio.api.notice;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.property.PropertyAccessService;
import com.dwellio.api.property.PropertyEntity;
import com.dwellio.api.user.AppUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final PropertyAccessService propertyAccessService;

    public NoticeService(NoticeRepository noticeRepository, PropertyAccessService propertyAccessService) {
        this.noticeRepository = noticeRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional(readOnly = true)
    public List<NoticeResponse> list(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        return noticeRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NoticeResponse create(AppUserEntity user, UUID propertyId, CreateNoticeRequest request) {
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, propertyId);
        Instant now = Instant.now();
        NoticeEntity entity = noticeRepository.save(new NoticeEntity(
                UUID.randomUUID(),
                property.getId(),
                request.title().trim(),
                MarkdownSanitizer.sanitize(request.body()),
                "DRAFT",
                null,
                request.expiresAt(),
                now,
                now
        ));
        return toResponse(entity);
    }

    @Transactional
    public NoticeResponse update(AppUserEntity user, UUID noticeId, UpdateNoticeRequest request) {
        NoticeEntity entity = requireNotice(noticeId);
        propertyAccessService.requireInventoryMutator(user, entity.getPropertyId());

        if (request.title() != null && !request.title().isBlank()) {
            entity.setTitle(request.title().trim());
        }
        if (request.body() != null) {
            entity.setBody(MarkdownSanitizer.sanitize(request.body()));
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }
        entity.setUpdatedAt(Instant.now());
        return toResponse(entity);
    }

    @Transactional
    public NoticeResponse publish(AppUserEntity user, UUID noticeId) {
        NoticeEntity entity = requireNotice(noticeId);
        propertyAccessService.requireInventoryMutator(user, entity.getPropertyId());
        if ("PUBLISHED".equals(entity.getStatus())) {
            return toResponse(entity);
        }
        Instant now = Instant.now();
        entity.setStatus("PUBLISHED");
        entity.setPublishedAt(now);
        entity.setUpdatedAt(now);
        return toResponse(entity);
    }

    @Transactional
    public void delete(AppUserEntity user, UUID noticeId) {
        NoticeEntity entity = requireNotice(noticeId);
        propertyAccessService.requireInventoryMutator(user, entity.getPropertyId());
        noticeRepository.delete(entity);
    }

    private NoticeEntity requireNotice(UUID noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Notice not found"));
    }

    private NoticeResponse toResponse(NoticeEntity entity) {
        return new NoticeResponse(
                entity.getId(),
                entity.getPropertyId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getStatus(),
                entity.getPublishedAt(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
