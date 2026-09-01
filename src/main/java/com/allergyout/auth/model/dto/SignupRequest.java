package com.allergyout.auth.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
        @NotBlank String memberId,
        @NotBlank String memberPwd,
        @NotBlank String memberName,
        @NotBlank String phone,
        @NotBlank @Email String email
) {
}
