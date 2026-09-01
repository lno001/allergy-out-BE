package com.allergyout.auth.model.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allergyout.auth.model.dao.TokenMapper;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.global.security.CookieUtil;
import com.allergyout.global.security.JwtUtil;
import com.allergyout.member.model.dao.MemberMapper;
import com.allergyout.member.model.vo.Member;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenMapper tokenMapper;
    private final MemberMapper memberMapper;
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;

    @Transactional
    public void createAuthTokens(Member member, HttpServletResponse response) {
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

    @Transactional
    public void refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtil.getCookie(request, CookieUtil.REFRESH_COOKIE);
        if (refreshToken == null
                || !jwtUtil.isValidToken(refreshToken)
                || !"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
            rejectRefresh(response);
        }

        String jti = jwtUtil.getJti(refreshToken);
        if (tokenMapper.countValidToken(jti, System.currentTimeMillis()) == 0) {
            memberMapper.findByMemberId(jwtUtil.getSubject(refreshToken))
                    .ifPresent(m -> tokenMapper.deleteByMemberNo(m.getMemberNo()));
            rejectRefresh(response);
        }

        tokenMapper.deleteByToken(jti);

        Member member = memberMapper.findByMemberId(jwtUtil.getSubject(refreshToken)).orElse(null);
        if (member == null) {
            rejectRefresh(response);
        }

        createAuthTokens(member, response);
    }

    @Transactional
    public void deleteAuthTokens(HttpServletRequest request, HttpServletResponse response) {
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

    private void rejectRefresh(HttpServletResponse response) {
        cookieUtil.deleteAuthCookies(response);
        throw new CustomException(ErrorCode.UNAUTHORIZED);
    }
}