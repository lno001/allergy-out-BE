package com.allergyout.member.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MemberPwdUpdateRequest(
        @NotBlank(message = "기존 비밀번호를 입력해주세요.")
        String currentPassword,

        // 영문+숫자 포함 8~20자 (MEMBER.MEMBER_PWD VARCHAR2(200)는 해시 저장 길이라 정책값과 무관) / 명세서 검증 실패 메시지
        @NotBlank(message = "비밀번호는 영문, 숫자를 포함하여 8자 이상 20자 이하로 입력해주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,20}$",
                message = "비밀번호는 영문, 숫자를 포함하여 8자 이상 20자 이하로 입력해주세요."
        )
        String newPassword
) {}
