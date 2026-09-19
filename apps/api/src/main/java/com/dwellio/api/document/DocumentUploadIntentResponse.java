package com.dwellio.api.document;

public record DocumentUploadIntentResponse(
        DocumentResponse document,
        String uploadUrl
) {
}
