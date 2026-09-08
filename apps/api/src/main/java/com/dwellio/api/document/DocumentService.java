package com.dwellio.api.document;

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
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final PropertyAccessService propertyAccessService;
    private final ObjectStorage objectStorage;

    public DocumentService(
            DocumentRepository documentRepository,
            PropertyAccessService propertyAccessService,
            ObjectStorage objectStorage
    ) {
        this.documentRepository = documentRepository;
        this.propertyAccessService = propertyAccessService;
        this.objectStorage = objectStorage;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(AppUserEntity user, UUID propertyId) {
        propertyAccessService.requireReadableProperty(user, propertyId);
        return documentRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(AppUserEntity user, UUID documentId) {
        DocumentEntity entity = requireDocument(documentId);
        if (entity.getPropertyId() != null) {
            propertyAccessService.requireReadableProperty(user, entity.getPropertyId());
        } else {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Document not found");
        }
        return toResponse(entity);
    }

    @Transactional
    public DocumentUploadIntentResponse createUploadIntent(
            AppUserEntity user,
            UUID propertyId,
            CreateDocumentRequest request
    ) {
        PropertyEntity property = propertyAccessService.requireInventoryMutator(user, propertyId);
        UUID documentId = UUID.randomUUID();
        String contentType = request.contentType() == null || request.contentType().isBlank()
                ? "application/octet-stream"
                : request.contentType().trim();
        String storageKey = property.getOrganizationId() + "/" + property.getId() + "/" + documentId;
        Instant now = Instant.now();
        DocumentEntity entity = documentRepository.save(new DocumentEntity(
                documentId,
                property.getOrganizationId(),
                property.getId(),
                request.tenantId(),
                request.tenancyId(),
                request.name().trim(),
                storageKey,
                contentType,
                null,
                "PENDING_UPLOAD",
                now,
                now
        ));
        String uploadUrl = objectStorage.createPresignedPutUrl(storageKey, contentType);
        return new DocumentUploadIntentResponse(toResponse(entity), uploadUrl);
    }

    @Transactional
    public DocumentResponse complete(AppUserEntity user, UUID documentId) {
        DocumentEntity entity = requireDocument(documentId);
        requireMutatorForDocument(user, entity);
        if ("UPLOADED".equals(entity.getStatus())) {
            return toResponse(entity);
        }
        if (!"PENDING_UPLOAD".equals(entity.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Document is not pending upload");
        }
        if (!objectStorage.objectExists(entity.getStorageKey())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Object not found in storage; upload before complete"
            );
        }
        Instant now = Instant.now();
        entity.setStatus("UPLOADED");
        entity.setUpdatedAt(now);
        return toResponse(entity);
    }

    @Transactional
    public void delete(AppUserEntity user, UUID documentId) {
        DocumentEntity entity = requireDocument(documentId);
        requireMutatorForDocument(user, entity);
        String key = entity.getStorageKey();
        documentRepository.delete(entity);
        objectStorage.deleteObject(key);
    }

    private void requireMutatorForDocument(AppUserEntity user, DocumentEntity entity) {
        if (entity.getPropertyId() == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Document not found");
        }
        propertyAccessService.requireInventoryMutator(user, entity.getPropertyId());
    }

    private DocumentEntity requireDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Document not found"));
    }

    private DocumentResponse toResponse(DocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getPropertyId(),
                entity.getTenantId(),
                entity.getTenancyId(),
                entity.getName(),
                entity.getStorageKey(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
