package com.allergyout.member.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberEmailUpdateRequest(
        // MEMBER.EMAIL VARCHAR2(50) · ASCII 형식만 (@ 1개 + TLD 2~24자, 한글 불가) — SignupRequest 와 동일
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(
                regexp = "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,255}\\.[A-Za-z]{2,24}$",
                message = "올바른 이메일 형식이 아닙니다."
        )
        @Size(max = 50, message = "올바른 이메일 형식이 아닙니다.")
        String email
) {}
