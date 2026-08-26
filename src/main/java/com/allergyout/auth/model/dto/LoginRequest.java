package com.allergyout.auth.model.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String memberId,
        @NotBlank String password
) {
}
