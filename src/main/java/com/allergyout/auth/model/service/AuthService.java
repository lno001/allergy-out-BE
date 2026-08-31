package com.allergyout.auth.model.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allergyout.auth.model.dao.TokenMapper;
import com.allergyout.auth.model.dto.LoginRequest;
import com.allergyout.auth.model.dto.MemberLoginResponse;
import com.allergyout.auth.model.dto.SignupRequest;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.global.security.CookieUtil;
import com.allergyout.global.security.CustomUserDetails;
import com.allergyout.global.security.JwtUtil;
import com.allergyout.member.model.dao.MemberMapper;
import com.allergyout.member.model.vo.Member;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final MemberMapper memberMapper;
	private final TokenService tokenService;
	private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signup(SignupRequest request) {
        if (memberMapper.existsByMemberId(request.memberId())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (memberMapper.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (memberMapper.existsByPhone(request.phone())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Member member = Member.builder()
                .memberId(request.memberId())
                .memberPwd(passwordEncoder.encode(request.memberPwd()))
                .memberName(request.memberName())
                .phone(request.phone())
                .email(request.email())
                .build();

        memberMapper.insertMember(member);
    }
    
    @Transactional
    public MemberLoginResponse login(LoginRequest request, HttpServletResponse response) {
        Member member = memberMapper.findByMemberId(request.memberId())
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.memberPwd(), member.getMemberPwd())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        tokenService.createAuthTokens(member, response);
        return toMemberLoginResponse(member);
    }

    @Transactional
    public void refreshToken(HttpServletRequest request, HttpServletResponse response) {
        tokenService.refreshToken(request, response);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        tokenService.deleteAuthTokens(request, response);
    }

    @Transactional(readOnly = true)
    public MemberLoginResponse getMe(CustomUserDetails user) {
        if (user == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        Member member = memberMapper.findByMemberId(user.getMemberId())
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
        return toMemberLoginResponse(member);
    }

    private MemberLoginResponse toMemberLoginResponse(Member member) {
        return new MemberLoginResponse(
                member.getMemberNo(),
                member.getMemberId(),
                member.getMemberName(),
                member.getRole(),
                member.getMemberImg());
    }
}