package com.allergyout.auth.model.dto;

public record MemberLoginResponse(
        String accessToken,
        Long memberNo,
        String memberId,
        String memberName,
        String role,
        String memberImg
) {}