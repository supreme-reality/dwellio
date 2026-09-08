package com.dwellio.api.document;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
public class DocumentController {

    private final CurrentUserService currentUserService;
    private final DocumentService documentService;

    public DocumentController(CurrentUserService currentUserService, DocumentService documentService) {
        this.currentUserService = currentUserService;
        this.documentService = documentService;
    }

    @GetMapping("/properties/{propertyId}/documents")
    public List<DocumentResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return documentService.list(user, propertyId);
    }

    @PostMapping("/properties/{propertyId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentUploadIntentResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateDocumentRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return documentService.createUploadIntent(user, propertyId, request);
    }

    @GetMapping("/documents/{documentId}")
    public DocumentResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID documentId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return documentService.get(user, documentId);
    }

    @PostMapping("/documents/{documentId}/complete")
    public DocumentResponse complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID documentId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return documentService.complete(user, documentId);
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID documentId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        documentService.delete(user, documentId);
    }
}
