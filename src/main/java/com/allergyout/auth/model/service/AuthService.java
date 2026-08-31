package com.allergyout.auth.model.service;

import java.util.Map;

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
    private final TokenMapper tokenMapper;
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signup(SignupRequest request) {
        if (memberMapper.existsByMemberId(request.memberId())) {
            throw new CustomException(ErrorCode.DUPLICATE_VALUE, Map.of("memberId", "이미 사용 중인 아이디입니다."));
        }
        if (memberMapper.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_VALUE, Map.of("email", "이미 사용 중인 이메일입니다."));
        }
        if (memberMapper.existsByPhone(request.phone())) {
            throw new CustomException(ErrorCode.DUPLICATE_VALUE, Map.of("phone", "이미 사용 중인 연락처입니다."));
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

        createAuthTokens(member, response);
        return toMemberLoginResponse(member);
    }

    @Transactional
    public void refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtil.getCookie(request, CookieUtil.REFRESH_COOKIE);
        if (refreshToken == null
                || !jwtUtil.isValidToken(refreshToken)
                || !"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
            cookieUtil.deleteAuthCookies(response);
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        String jti = jwtUtil.getJti(refreshToken);
        if (tokenMapper.countValidToken(jti, System.currentTimeMillis()) == 0) {
            memberMapper.findByMemberId(jwtUtil.getSubject(refreshToken))
                    .ifPresent(m -> tokenMapper.deleteByMemberNo(m.getMemberNo()));
            cookieUtil.deleteAuthCookies(response);
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        tokenMapper.deleteByToken(jti);

        Member member = memberMapper.findByMemberId(jwtUtil.getSubject(refreshToken))
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
        createAuthTokens(member, response);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtil.getCookie(request, CookieUtil.REFRESH_COOKIE);
        if (refreshToken != null && jwtUtil.isValidToken(refreshToken)) {
            tokenMapper.deleteByToken(jwtUtil.getJti(refreshToken));
        } else {
            String accessToken = cookieUtil.getCookie(request, CookieUtil.ACCESS_COOKIE);
            if (accessToken != null && jwtUtil.isValidToken(accessToken)) {
                memberMapper.findByMemberId(jwtUtil.getSubject(accessToken))
                        .ifPresent(m -> tokenMapper.deleteByMemberNo(m.getMemberNo()));
            }
        }
        cookieUtil.deleteAuthCookies(response);
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

    private void createAuthTokens(Member member, HttpServletResponse response) {
        tokenMapper.deleteByMemberNo(member.getMemberNo());

        String accessToken = jwtUtil.createAccessToken(
                member.getMemberId(), member.getMemberNo(), member.getRole());
        String refreshToken = jwtUtil.createRefreshToken(
                member.getMemberId(), member.getMemberNo());

        tokenMapper.insertToken(
                member.getMemberNo(),
                jwtUtil.getJti(refreshToken),
                jwtUtil.getExpiration(refreshToken).getTime());

        cookieUtil.addAccessToken(response, accessToken);
        cookieUtil.addRefreshToken(response, refreshToken);
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