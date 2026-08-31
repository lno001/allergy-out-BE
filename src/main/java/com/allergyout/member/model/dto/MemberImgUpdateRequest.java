package com.allergyout.member.model.dto;

import org.springframework.web.multipart.MultipartFile;

// @NotNull 안 붙임: 파일 없음/확장자/용량 검증은 S3Service가 담당하고,
// 명세서가 요구하는 개별 메시지("이미지 파일을 선택해주세요." 등)도 S3Service가 냄.
// @NotNull을 붙이면 @Valid가 먼저 가로채 일반 메시지가 나가 명세서와 어긋남.
public record MemberImgUpdateRequest(MultipartFile memberImg) {}
