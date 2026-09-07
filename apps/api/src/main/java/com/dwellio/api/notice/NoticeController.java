package com.dwellio.api.notice;

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
public class NoticeController {

    private final CurrentUserService currentUserService;
    private final NoticeService noticeService;

    public NoticeController(CurrentUserService currentUserService, NoticeService noticeService) {
        this.currentUserService = currentUserService;
        this.noticeService = noticeService;
    }

    @GetMapping("/properties/{propertyId}/notices")
    public List<NoticeResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return noticeService.list(user, propertyId);
    }

    @PostMapping("/properties/{propertyId}/notices")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateNoticeRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return noticeService.create(user, propertyId, request);
    }

    @PatchMapping("/notices/{noticeId}")
    public NoticeResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID noticeId,
            @Valid @RequestBody UpdateNoticeRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return noticeService.update(user, noticeId, request);
    }

    @PostMapping("/notices/{noticeId}/publish")
    public NoticeResponse publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID noticeId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return noticeService.publish(user, noticeId);
    }

    @DeleteMapping("/notices/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID noticeId
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        noticeService.delete(user, noticeId);
    }
}
