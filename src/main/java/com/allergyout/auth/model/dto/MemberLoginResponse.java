package com.allergyout.auth.model.dto;

public record MemberLoginResponse(
        Long memberNo,
        String memberId,
        String memberName,
        String role,
        String memberImg
) {}