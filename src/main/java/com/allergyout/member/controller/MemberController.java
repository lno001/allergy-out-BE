package com.allergyout.member.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.allergyout.global.common.ApiResponse;
import com.allergyout.global.security.CustomUserDetails;
import com.allergyout.member.model.dto.MemberEmailResponse;
import com.allergyout.member.model.dto.MemberEmailUpdateRequest;
import com.allergyout.member.model.dto.MemberImgResponse;
import com.allergyout.member.model.dto.MemberImgUpdateRequest;
import com.allergyout.member.model.dto.MemberNameResponse;
import com.allergyout.member.model.dto.MemberNameUpdateRequest;
import com.allergyout.member.model.dto.MemberPhoneResponse;
import com.allergyout.member.model.dto.MemberPhoneUpdateRequest;
import com.allergyout.member.model.dto.MemberPwdUpdateRequest;
import com.allergyout.member.model.dto.MemberResponse;
import com.allergyout.member.model.dto.MemberWithdrawRequest;
import com.allergyout.member.model.service.MemberService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public ResponseEntity<ApiResponse<MemberResponse>> getMember(
            @AuthenticationPrincipal CustomUserDetails user) {
        MemberResponse data = memberService.getMember(user.getMemberNo());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("마이페이지 조회 성공", data));
    }

    @PatchMapping("/membername")
    public ResponseEntity<ApiResponse<MemberNameResponse>> updateMemberName(
            @Valid @RequestBody MemberNameUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        MemberNameResponse data = memberService.updateMemberName(user.getMemberNo(), request.memberName());
        return ResponseEntity.ok(ApiResponse.success("이름 수정 성공", data));
    }

    @PatchMapping("/email")
    public ResponseEntity<ApiResponse<MemberEmailResponse>> updateMemberEmail(
            @Valid @RequestBody MemberEmailUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        MemberEmailResponse data = memberService.updateMemberEmail(user.getMemberNo(), request.email());
        return ResponseEntity.ok(ApiResponse.success("이메일 수정 성공", data));
    }

    @PatchMapping("/phone")
    public ResponseEntity<ApiResponse<MemberPhoneResponse>> updateMemberPhone(
            @Valid @RequestBody MemberPhoneUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        MemberPhoneResponse data = memberService.updateMemberPhone(user.getMemberNo(), request.phone());
        return ResponseEntity.ok(ApiResponse.success("연락처 수정 성공", data));
    }

    @PatchMapping("/memberpwd")
    public ResponseEntity<ApiResponse<Void>> updateMemberPwd(
            @Valid @RequestBody MemberPwdUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        memberService.updateMemberPwd(user.getMemberNo(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("비밀번호 변경 성공", null));
    }

    @PatchMapping(value = "/memberImg", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MemberImgResponse>> updateMemberImg(
            @ModelAttribute MemberImgUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        MemberImgResponse data = memberService.updateMemberImg(user.getMemberNo(), request.memberImg());
        return ResponseEntity.ok(ApiResponse.success("프로필 사진 수정 성공", data));
    }

    @DeleteMapping("/memberImg")
    public ResponseEntity<ApiResponse<Void>> deleteMemberImg(
            @AuthenticationPrincipal CustomUserDetails user) {
        memberService.deleteMemberImg(user.getMemberNo());
        return ResponseEntity.ok(ApiResponse.success("프로필 사진 삭제 성공", null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteMember(
            @Valid @RequestBody MemberWithdrawRequest request,
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletResponse response) {
        memberService.deleteMember(user.getMemberNo(), request.memberPwd(), response);
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴가 완료되었습니다.", null));
    }
}
