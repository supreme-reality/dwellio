package com.dwellio.api.security;

import com.dwellio.api.common.ApiException;
import com.dwellio.api.common.ErrorCode;
import com.dwellio.api.user.AppUserEntity;
import com.dwellio.api.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public CurrentUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public AppUserEntity upsertFromJwt(Jwt jwt) {
        String email = extractEmail(jwt);
        String name = extractName(jwt, email);
        Instant now = Instant.now();

        return appUserRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    if (!name.equals(existing.getName())) {
                        existing.setName(name);
                        existing.setUpdatedAt(now);
                        return appUserRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> appUserRepository.save(
                        new AppUserEntity(UUID.randomUUID(), email, name, "ACTIVE", now, now)
                ));
    }

    private static String extractEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        for (Map.Entry<String, Object> entry : jwt.getClaims().entrySet()) {
            if (entry.getKey().endsWith("/email") && entry.getValue() instanceof String value && !value.isBlank()) {
                return value.trim().toLowerCase(Locale.ROOT);
            }
        }
        throw new ApiException(
                ErrorCode.UNAUTHORIZED,
                HttpStatus.UNAUTHORIZED,
                "Access token is missing an email claim",
                Map.of()
        );
    }

    private static String extractName(Jwt jwt, String email) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        for (Map.Entry<String, Object> entry : jwt.getClaims().entrySet()) {
            if (entry.getKey().endsWith("/name") && entry.getValue() instanceof String value && !value.isBlank()) {
                return value.trim();
            }
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
