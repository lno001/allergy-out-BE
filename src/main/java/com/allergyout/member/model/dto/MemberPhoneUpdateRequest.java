package com.allergyout.member.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MemberPhoneUpdateRequest(
        // MEMBER.PHONE VARCHAR2(20) · 010 + 숫자 8자리 (하이픈 없이 숫자만) — SignupRequest 와 동일
        @NotNull(message = "연락처를 입력해주세요.")
        @Pattern(
                regexp = "^010[0-9]{8}$",
                message = "올바른 연락처 형식이 아닙니다."
        )
        String phone
) {}
