package com.allergyout.auth.model.service;

import java.util.Locale;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allergyout.auth.model.dto.LoginRequest;
import com.allergyout.auth.model.dto.MemberLoginResponse;
import com.allergyout.auth.model.dto.SignupRequest;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.global.security.CustomUserDetails;
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
        String email = request.email().toLowerCase(Locale.ROOT); // 이메일은 소문자로 정규화해 저장·비교

        if (memberMapper.isDuplicateMemberId(request.memberId())) {
            throw new CustomException(ErrorCode.DUPLICATE_VALUE, Map.of("memberId", "이미 사용 중인 아이디입니다."));
        }
        if (memberMapper.isDuplicateEmail(email)) {
            throw new CustomException(ErrorCode.DUPLICATE_VALUE, Map.of("email", "이미 사용 중인 이메일입니다."));
        }
        if (memberMapper.isDuplicatePhone(request.phone())) {
            throw new CustomException(ErrorCode.DUPLICATE_VALUE, Map.of("phone", "이미 사용 중인 연락처입니다."));
        }

        Member member = Member.builder()
                .memberId(request.memberId())
                .memberPwd(passwordEncoder.encode(request.memberPwd()))
                .memberName(request.memberName())
                .phone(request.phone())
                .email(email)
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

        String accessToken = tokenService.createAuthTokens(member, response);
        return toMemberLoginResponse(accessToken, member);
    }

    @Transactional
    public String refreshToken(HttpServletRequest request, HttpServletResponse response) {
        return tokenService.refreshToken(request, response);
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

    private MemberLoginResponse toMemberLoginResponse(String accessToken, Member member) {
        return new MemberLoginResponse(
                accessToken,
                member.getMemberNo(),
                member.getMemberId(),
                member.getMemberName(),
                member.getRole(),
                member.getMemberImg(),
                member.getMemberImgPath());
    }
    
    private MemberLoginResponse toMemberLoginResponse(Member member) {
        return toMemberLoginResponse(null, member);
    }
}