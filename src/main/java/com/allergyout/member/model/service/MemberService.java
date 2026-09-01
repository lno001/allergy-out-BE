package com.allergyout.member.model.service;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.allergyout.auth.model.dao.TokenMapper;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.global.security.CookieUtil;
import com.allergyout.member.model.dao.MemberMapper;
import com.allergyout.member.model.dto.MemberEmailResponse;
import com.allergyout.member.model.dto.MemberImgResponse;
import com.allergyout.member.model.dto.MemberNameResponse;
import com.allergyout.member.model.dto.MemberPhoneResponse;
import com.allergyout.member.model.dto.MemberResponse;
import com.allergyout.member.model.vo.Member;
import com.allergyout.s3.S3Service;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final String MEMBER_IMG_DIR = "members"; // S3 dirName, 하드코딩 상수(요청값 금지)

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final S3Service s3Service;
    private final TokenMapper tokenMapper;   // 탈퇴 시 리프레시 토큰 폐기 (auth 담당 DAO 재사용)
    private final CookieUtil cookieUtil;     // 탈퇴 시 인증 쿠키 삭제 (auth 담당 유틸 재사용)

    @Transactional(readOnly = true)
    public MemberResponse getMember(Long memberNo) {
        return MemberResponse.from(getMemberByNo(memberNo));
    }

    @Transactional
    public MemberNameResponse updateMemberName(Long memberNo, String memberName) {
        Member member = getMemberByNo(memberNo);
        if (memberName.equals(member.getMemberName())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, Map.of("memberName", "기존 이름과 동일합니다."));
        }
        memberMapper.updateMemberName(memberNo, memberName);
        return new MemberNameResponse(memberName);
    }

    @Transactional
    public MemberEmailResponse updateMemberEmail(Long memberNo, String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT); // 이메일은 소문자로 정규화해 저장·비교
        Member member = getMemberByNo(memberNo);
        if (normalizedEmail.equalsIgnoreCase(member.getEmail())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, Map.of("email", "기존 이메일과 동일합니다."));
        }
        // TODO(인증번호 플로우 - 이번 스코프 제외): 이메일 변경 전 인증번호 발송·검증 필요.
        //  별도 API(인증번호 발송/확인)와 저장소(코드·만료시각) 설계 후,
        //  이 지점에서 "memberNo가 이 email에 대해 인증 완료 상태인지" 확인하고 아니면 CustomException 던질 것.
        if (memberMapper.existsByEmailExcludingSelf(normalizedEmail, memberNo)) {
            throw new CustomException(ErrorCode.DUPLICATE_VALUE, Map.of("email", "이미 사용 중인 이메일입니다."));
        }
        memberMapper.updateMemberEmail(memberNo, normalizedEmail);
        return new MemberEmailResponse(normalizedEmail);
    }

    @Transactional
    public MemberPhoneResponse updateMemberPhone(Long memberNo, String phone) {
        Member member = getMemberByNo(memberNo);
        if (phone.equals(member.getPhone())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, Map.of("phone", "기존 연락처와 동일합니다."));
        }
        // TODO(인증번호 플로우 - 이번 스코프 제외): 연락처 변경 전 인증번호 발송·검증 필요.
        //  별도 API(인증번호 발송/확인)와 저장소(코드·만료시각) 설계 후,
        //  이 지점에서 "memberNo가 이 phone에 대해 인증 완료 상태인지" 확인하고 아니면 CustomException 던질 것.
        if (memberMapper.existsByPhoneExcludingSelf(phone, memberNo)) {
            throw new CustomException(ErrorCode.DUPLICATE_VALUE, Map.of("phone", "이미 사용 중인 연락처입니다."));
        }
        memberMapper.updateMemberPhone(memberNo, phone);
        return new MemberPhoneResponse(phone);
    }

    @Transactional
    public void updateMemberPwd(Long memberNo, String currentPassword, String newPassword) {
        Member member = getMemberByNo(memberNo);
        if (!passwordEncoder.matches(currentPassword, member.getMemberPwd())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(newPassword, member.getMemberPwd())) {
            throw new CustomException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }
        memberMapper.updateMemberPwd(memberNo, passwordEncoder.encode(newPassword));
    }

    @Transactional
    public MemberImgResponse updateMemberImg(Long memberNo, MultipartFile memberImg) {
        Member member = getMemberByNo(memberNo);
        // 파일 없음/확장자/용량 검증은 S3Service.upload() 내부에서 처리(현재 INVALID_INPUT_VALUE).
        //  명세서의 개별 메시지(파일 없음/확장자/용량)가 필요하면 S3Service(다른 담당)에서 세분화 ErrorCode로 교체 필요.
        // 업로드 후 이 트랜잭션이 롤백되면 S3에 고아 파일이 남음 → 정리 로직은 별도 담당.
        String newImgUrl = s3Service.upload(memberImg, MEMBER_IMG_DIR, memberNo);
        memberMapper.updateMemberImg(memberNo, memberImg.getOriginalFilename(), newImgUrl);
        if (member.getMemberImgPath() != null) {
            s3Service.delete(extractKey(member.getMemberImgPath()));
        }
        return new MemberImgResponse(newImgUrl);
    }

    @Transactional
    public void deleteMemberImg(Long memberNo) {
        Member member = getMemberByNo(memberNo);
        if (member.getMemberImgPath() == null) {
            throw new CustomException(ErrorCode.IMAGE_ALREADY_DEFAULT);
        }
        memberMapper.updateMemberImg(memberNo, null, null);
        s3Service.delete(extractKey(member.getMemberImgPath()));
    }

    // 회원 탈퇴: 본인 확인(비밀번호) → 소프트 삭제 → 모든 세션 토큰 폐기 + 이 브라우저 쿠키 삭제
    @Transactional
    public void deleteMember(Long memberNo, String memberPwd, HttpServletResponse response) {
        Member member = getMemberByNo(memberNo);
        if (!passwordEncoder.matches(memberPwd, member.getMemberPwd())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }
        memberMapper.updateMemberDelYn(memberNo);   // DEL_YN = 'Y'
        tokenMapper.deleteByMemberNo(memberNo);     // 그 회원의 리프레시 토큰 전부 삭제 (로그아웃은 현재 1개만)
        cookieUtil.deleteAuthCookies(response);     // refresh 쿠키 Max-Age=0 (access는 Bearer 헤더라 서버가 못 지움 → FE가 폐기)
    }

    // 회원 조회. 없으면(매퍼가 DEL_YN='N' 로 걸러 null) ENTITY_NOT_FOUND.
    private Member getMemberByNo(Long memberNo) {
        Member member = memberMapper.getMember(memberNo);
        if (member == null) {
            throw new CustomException(ErrorCode.ENTITY_NOT_FOUND);
        }
        return member;
    }

    // virtual-hosted-style URL(https://{bucket}.s3.{region}.amazonaws.com/{key})에서 key만 추출.
    // recipe 쪽도 동일 로직이 필요해 보여 나중에 S3Service 공용 유틸로 옮기는 걸 별도 제안 예정, 우선 로컬.
    private String extractKey(String imageUrl) {
        try {
            return URI.create(imageUrl).getPath().substring(1);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
