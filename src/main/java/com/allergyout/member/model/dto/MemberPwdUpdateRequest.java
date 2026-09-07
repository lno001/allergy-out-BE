package com.allergyout.member.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MemberPwdUpdateRequest(
        // 기존 비밀번호 확인용 — 저장된 해시와 대조만 하므로 형식 검증 없음
        @NotBlank(message = "기존 비밀번호를 입력해주세요.")
        String currentPassword,

        // 영문+숫자 필수, 특수문자 선택, 공백 불가, 8~30자 — SignupRequest.memberPwd 와 동일
        @NotNull(message = "새 비밀번호를 입력해주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,30}$",
                message = "비밀번호는 영문, 숫자를 포함하여 8자 이상 30자 이하로 입력해주세요. (특수문자 사용 가능, 공백 불가)"
        )
        String newPassword
) {}
