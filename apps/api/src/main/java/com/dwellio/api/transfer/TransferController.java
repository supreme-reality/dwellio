package com.dwellio.api.transfer;

import com.dwellio.api.security.CurrentUserService;
import com.dwellio.api.user.AppUserEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final CurrentUserService currentUserService;
    private final TransferService transferService;

    public TransferController(CurrentUserService currentUserService, TransferService transferService) {
        this.currentUserService = currentUserService;
        this.transferService = transferService;
    }

    @PostMapping("/preview")
    public TransferPreviewResponse preview(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransferPreviewRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return transferService.preview(user, request);
    }

    @PostMapping
    public TransferResponse execute(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransferExecuteRequest request
    ) {
        AppUserEntity user = currentUserService.upsertFromJwt(jwt);
        return transferService.execute(user, request);
    }
}
