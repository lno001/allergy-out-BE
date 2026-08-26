package com.allergyout.auth.model.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
