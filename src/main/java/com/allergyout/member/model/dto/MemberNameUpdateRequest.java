package com.allergyout.member.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberNameUpdateRequest(
        // MEMBER.MEMBER_NAME NVARCHAR2(30) / 명세서 검증 실패 메시지
        @NotBlank(message = "이름은 2자 이상 30자 이하로 입력해주세요.")
        @Size(min = 2, max = 30, message = "이름은 2자 이상 30자 이하로 입력해주세요.")
        String memberName
) {}
