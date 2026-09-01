package com.allergyout.member.model.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberDeleteRequest(
        @NotBlank(message = "비밀번호를 입력해주세요.")
        String memberPwd
) {}
