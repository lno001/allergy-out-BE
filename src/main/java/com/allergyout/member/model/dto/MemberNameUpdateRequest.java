package com.allergyout.member.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MemberNameUpdateRequest(
        // MEMBER.MEMBER_NAME NVARCHAR2(30) · 한글(완성형)+영문 2~30자 (공백·숫자·기호 불가) — SignupRequest 와 동일
        @NotNull(message = "이름을 입력해주세요.")
        @Pattern(
                regexp = "^[가-힣A-Za-z]{2,30}$",
                message = "이름은 공백 없이 한글, 영문 2자 이상 30자 이하로 입력해주세요."
        )
        String memberName
) {}
