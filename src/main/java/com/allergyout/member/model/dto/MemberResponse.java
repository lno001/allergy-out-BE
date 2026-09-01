package com.allergyout.member.model.dto;

import java.time.LocalDateTime;

import com.allergyout.member.model.vo.Member;

public record MemberResponse(
        String memberId,
        String memberImgPath,
        String memberName,
        String phone,
        String email,
        LocalDateTime createDate
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getMemberId(),
                member.getMemberImgPath(),
                member.getMemberName(),
                member.getPhone(),
                member.getEmail(),
                member.getCreateDate()
        );
    }
}