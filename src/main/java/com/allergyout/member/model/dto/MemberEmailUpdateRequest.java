package com.allergyout.member.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberEmailUpdateRequest(
        // MEMBER.EMAIL VARCHAR2(50) / 명세서 검증 실패 메시지
        @NotBlank(message = "올바른 이메일 형식이 아닙니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 50, message = "올바른 이메일 형식이 아닙니다.")
        String email
) {}
