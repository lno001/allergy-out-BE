package com.allergyout.allergy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.allergyout.allergy.model.dto.AllergyResponse;
import com.allergyout.allergy.model.dto.AllergyUpdateRequest;
import com.allergyout.allergy.model.service.AllergyService;
import com.allergyout.global.common.ApiResponse;
import com.allergyout.global.security.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class AllergyController {

    private final AllergyService allergyService;

    @GetMapping("/allergy")
    public ResponseEntity<ApiResponse<AllergyResponse>> getAllergyList(
            @AuthenticationPrincipal CustomUserDetails user) {
        AllergyResponse data = allergyService.getAllergyList(user.getMemberNo());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("알러지 정보 조회 성공", data));
    }

    @PatchMapping("/allergy")
    public ResponseEntity<ApiResponse<AllergyResponse>> updateAllergyList(
            @Valid @RequestBody AllergyUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        AllergyResponse data = allergyService.updateAllergyList(user.getMemberNo(), request.allergyList());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("알러지 필터 저장 성공", data));
    }
}
