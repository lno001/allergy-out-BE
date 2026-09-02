package com.allergyout.member.model.dto;

import org.springframework.web.multipart.MultipartFile;

// @NotNull 안 붙임: 파일 없음/확장자/용량 검증은 S3Service.validate() 가 담당 (현재 셋 다 INVALID_INPUT_VALUE).
// @NotNull을 붙이면 @Valid 가 먼저 가로채므로, 파일 검증을 S3Service 한 곳에 두기 위해 생략.
public record MemberImgUpdateRequest(MultipartFile memberImg) {}
