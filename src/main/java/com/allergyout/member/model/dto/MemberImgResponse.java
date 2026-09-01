package com.allergyout.member.model.dto;

// 프로필 사진 수정 응답: S3 URL(MEMBER_IMG_PATH). GET 조회 응답과 키 이름 통일.
public record MemberImgResponse(String memberImgPath) {}
