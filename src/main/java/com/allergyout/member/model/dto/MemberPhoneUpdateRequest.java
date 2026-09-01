package com.allergyout.member.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MemberPhoneUpdateRequest(
        // MEMBER.PHONE VARCHAR2(20) / 명세서 검증 실패 메시지
        @NotBlank(message = "연락처를 입력해주세요.")
        @Pattern(regexp = "^010[0-9]{8}$", message = "올바른 연락처 형식이 아닙니다.")
        String phone
) {}
