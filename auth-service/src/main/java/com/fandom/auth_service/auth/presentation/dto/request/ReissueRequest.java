package com.fandom.auth_service.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(
        @NotBlank String refreshToken
) {
}
