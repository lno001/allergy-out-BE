package com.allergyout.rasp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.allergyout.global.common.ApiResponse;
import com.allergyout.global.security.CustomUserDetails;
import com.allergyout.rasp.model.dto.DeviceResponse;
import com.allergyout.rasp.model.service.RaspService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rasp")
@RequiredArgsConstructor
public class RaspController {

    private final RaspService raspService;

    // POST /api/rasp/devices — 인증 필요. 로그인 회원의 라즈베리파이를 1회 등록(멱등).
    // 응답 data.deviceNo 를 step_sender 실행 인자로 넣는다.
    @PostMapping("/devices")
    public ResponseEntity<ApiResponse<DeviceResponse>> createDevice(
            @AuthenticationPrincipal CustomUserDetails user) {
        DeviceResponse data = raspService.createDevice(user.getMemberNo());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("디바이스를 등록했습니다.", data));
    }

    // GET /api/rasp/devices — 인증 필요. 내 deviceNo 조회 (잊었을 때 재확인). 미등록이면 404.
    @GetMapping("/devices")
    public ResponseEntity<ApiResponse<DeviceResponse>> getDevice(
            @AuthenticationPrincipal CustomUserDetails user) {
        DeviceResponse data = raspService.getDevice(user.getMemberNo());
        return ResponseEntity.ok(ApiResponse.success("디바이스를 조회했습니다.", data));
    }
}
