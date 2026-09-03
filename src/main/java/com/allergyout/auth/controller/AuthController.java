package com.allergyout.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.allergyout.auth.model.dto.AccessTokenResponse;
import com.allergyout.auth.model.dto.LoginRequest;
import com.allergyout.auth.model.dto.MemberLoginResponse;
import com.allergyout.auth.model.dto.SignupRequest;
import com.allergyout.auth.model.service.AuthService;
import com.allergyout.global.common.ApiResponse;
import com.allergyout.global.security.CustomUserDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest request) {
    	authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created("회원가입 성공", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberLoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        MemberLoginResponse data = authService.login(request, response);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        String accessToken = authService.refreshToken(request, response);
        if (accessToken == null) {
            return ResponseEntity.ok(ApiResponse.success("비로그인", null));
        }
        return ResponseEntity.ok(
                ApiResponse.success("토큰 재발급", new AccessTokenResponse(accessToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberLoginResponse>> getMe(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success("조회 성공", authService.getMe(user)));
    }
}