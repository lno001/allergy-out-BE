package com.allergyout.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
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

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberMapper memberMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private S3Service s3Service;
    @Mock
    private TokenMapper tokenMapper;
    @Mock
    private CookieUtil cookieUtil;

    @InjectMocks
    private MemberService memberService;

    private static final long MEMBER_NO = 1L;
    private static final String ENCODED_PWD = "encoded-current-pwd";
    private static final String IMG_URL =
            "https://bucket.s3.ap-northeast-2.amazonaws.com/members/1/old_260830.jpg";

    private Member memberWithoutImg() {
        return baseBuilder().memberImg(null).memberImgPath(null).build();
    }

    private Member memberWithImg() {
        return baseBuilder().memberImg("old.jpg").memberImgPath(IMG_URL).build();
    }

    private Member.MemberBuilder baseBuilder() {
        return Member.builder()
                .memberNo(MEMBER_NO)
                .memberId("minjai")
                .memberPwd(ENCODED_PWD)
                .memberName("김민재")
                .phone("01012341234")
                .email("allergyout@gmail.com")
                .role("ROLE_USER")
                .createDate(LocalDateTime.of(2025, 8, 20, 14, 30, 0))
                .delYn("N");
    }

    @Nested
    @DisplayName("getMember")
    class GetMember {

        @Test
        @DisplayName("존재하는 회원이면 MemberResponse를 반환한다")
        void success() {
            Member member = memberWithImg();
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member);

            MemberResponse result = memberService.getMember(MEMBER_NO);

            assertThat(result.memberId()).isEqualTo("minjai");
            assertThat(result.memberImgPath()).isEqualTo(IMG_URL);
            assertThat(result.memberName()).isEqualTo("김민재");
            assertThat(result.phone()).isEqualTo("01012341234");
            assertThat(result.email()).isEqualTo("allergyout@gmail.com");
            assertThat(result.createDate()).isEqualTo(LocalDateTime.of(2025, 8, 20, 14, 30, 0));
        }

        @Test
        @DisplayName("회원이 없으면 ENTITY_NOT_FOUND")
        void notFound() {
            when(memberMapper.getMember(999L)).thenReturn(null);

            assertThatThrownBy(() -> memberService.getMember(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("updateMemberName")
    class UpdateMemberName {

        @Test
        @DisplayName("성공 시 매퍼 호출 후 새 이름을 반환한다")
        void success() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());

            MemberNameResponse result = memberService.updateMemberName(MEMBER_NO, "운영자");

            assertThat(result.memberName()).isEqualTo("운영자");
            verify(memberMapper).updateMemberName(MEMBER_NO, "운영자");
        }

        @Test
        @DisplayName("회원이 없으면 ENTITY_NOT_FOUND, 매퍼 update 미호출")
        void notFound() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(null);

            assertThatThrownBy(() -> memberService.updateMemberName(MEMBER_NO, "운영자"))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
            verify(memberMapper, never()).updateMemberName(any(), any());
        }
    }

    @Nested
    @DisplayName("updateMemberEmail")
    class UpdateMemberEmail {

        @Test
        @DisplayName("중복 아니면 수정 후 이메일을 반환한다")
        void success() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(memberMapper.existsByEmailExcludingSelf("new@test.com", MEMBER_NO)).thenReturn(false);

            MemberEmailResponse result = memberService.updateMemberEmail(MEMBER_NO, "new@test.com");

            assertThat(result.email()).isEqualTo("new@test.com");
            verify(memberMapper).updateMemberEmail(MEMBER_NO, "new@test.com");
        }

        @Test
        @DisplayName("다른 회원이 사용 중이면 DUPLICATE_VALUE + data{email}, update 미호출")
        void duplicated() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(memberMapper.existsByEmailExcludingSelf("dup@test.com", MEMBER_NO)).thenReturn(true);

            assertThatThrownBy(() -> memberService.updateMemberEmail(MEMBER_NO, "dup@test.com"))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_VALUE);
                        assertThat(ce.getDetails()).containsExactly(entry("email", "이미 사용 중인 이메일입니다."));
                    });
            verify(memberMapper, never()).updateMemberEmail(any(), any());
        }

        @Test
        @DisplayName("회원이 없으면 ENTITY_NOT_FOUND")
        void notFound() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(null);

            assertThatThrownBy(() -> memberService.updateMemberEmail(MEMBER_NO, "new@test.com"))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("updateMemberPhone")
    class UpdateMemberPhone {

        @Test
        @DisplayName("중복 아니면 수정 후 연락처를 반환한다")
        void success() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(memberMapper.existsByPhoneExcludingSelf("01099998888", MEMBER_NO)).thenReturn(false);

            MemberPhoneResponse result = memberService.updateMemberPhone(MEMBER_NO, "01099998888");

            assertThat(result.phone()).isEqualTo("01099998888");
            verify(memberMapper).updateMemberPhone(MEMBER_NO, "01099998888");
        }

        @Test
        @DisplayName("다른 회원이 사용 중이면 DUPLICATE_VALUE + data{phone}, update 미호출")
        void duplicated() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(memberMapper.existsByPhoneExcludingSelf("01099998888", MEMBER_NO)).thenReturn(true);

            assertThatThrownBy(() -> memberService.updateMemberPhone(MEMBER_NO, "01099998888"))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_VALUE);
                        assertThat(ce.getDetails()).containsExactly(entry("phone", "이미 사용 중인 연락처입니다."));
                    });
            verify(memberMapper, never()).updateMemberPhone(any(), any());
        }
    }

    @Nested
    @DisplayName("updateMemberPwd")
    class UpdateMemberPwd {

        @Test
        @DisplayName("기존 일치 + 새 비번이 다르면 인코딩해 저장한다")
        void success() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(passwordEncoder.matches("qwer1234", ENCODED_PWD)).thenReturn(true);
            when(passwordEncoder.matches("asdf1234", ENCODED_PWD)).thenReturn(false);
            when(passwordEncoder.encode("asdf1234")).thenReturn("encoded-new-pwd");

            memberService.updateMemberPwd(MEMBER_NO, "qwer1234", "asdf1234");

            verify(memberMapper).updateMemberPwd(MEMBER_NO, "encoded-new-pwd");
        }

        @Test
        @DisplayName("기존 비번 불일치면 PASSWORD_MISMATCH")
        void mismatch() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(passwordEncoder.matches("wrong", ENCODED_PWD)).thenReturn(false);

            assertThatThrownBy(() -> memberService.updateMemberPwd(MEMBER_NO, "wrong", "asdf1234"))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
            verify(memberMapper, never()).updateMemberPwd(any(), any());
        }

        @Test
        @DisplayName("새 비번이 기존과 같으면 PASSWORD_SAME_AS_OLD")
        void sameAsOld() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(passwordEncoder.matches("qwer1234", ENCODED_PWD)).thenReturn(true);

            assertThatThrownBy(() -> memberService.updateMemberPwd(MEMBER_NO, "qwer1234", "qwer1234"))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PASSWORD_SAME_AS_OLD);
            verify(memberMapper, never()).updateMemberPwd(any(), any());
        }
    }

    @Nested
    @DisplayName("updateMemberImg")
    class UpdateMemberImg {

        private final MultipartFile file =
                new MockMultipartFile("memberImg", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});

        @Test
        @DisplayName("기존 이미지가 없으면 업로드 URL 저장만 하고 delete는 호출하지 않는다")
        void success_noPreviousImg() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(s3Service.upload(any(), eq("members"), eq(MEMBER_NO)))
                    .thenReturn("https://bucket.s3.ap-northeast-2.amazonaws.com/members/1/new_260831.jpg");

            MemberImgResponse result = memberService.updateMemberImg(MEMBER_NO, file);

            assertThat(result.memberImgPath())
                    .isEqualTo("https://bucket.s3.ap-northeast-2.amazonaws.com/members/1/new_260831.jpg");
            verify(memberMapper).updateMemberImg(
                    MEMBER_NO, "photo.jpg", "https://bucket.s3.ap-northeast-2.amazonaws.com/members/1/new_260831.jpg");
            verify(s3Service, never()).delete(any());
        }

        @Test
        @DisplayName("기존 이미지가 있으면 새로 저장 후 기존 S3 객체를 key로 삭제한다")
        void success_replacePreviousImg() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithImg());
            when(s3Service.upload(any(), eq("members"), eq(MEMBER_NO)))
                    .thenReturn("https://bucket.s3.ap-northeast-2.amazonaws.com/members/1/new_260831.jpg");

            memberService.updateMemberImg(MEMBER_NO, file);

            verify(memberMapper).updateMemberImg(
                    MEMBER_NO, "photo.jpg", "https://bucket.s3.ap-northeast-2.amazonaws.com/members/1/new_260831.jpg");
            verify(s3Service).delete("members/1/old_260830.jpg");
        }

        @Test
        @DisplayName("회원이 없으면 ENTITY_NOT_FOUND, 업로드하지 않는다")
        void notFound() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(null);

            assertThatThrownBy(() -> memberService.updateMemberImg(MEMBER_NO, file))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
            verify(s3Service, never()).upload(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("deleteMemberImg")
    class DeleteMemberImg {

        @Test
        @DisplayName("이미지가 있으면 경로를 null로 갱신하고 S3 객체를 삭제한다")
        void success() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithImg());

            memberService.deleteMemberImg(MEMBER_NO);

            verify(memberMapper).updateMemberImg(MEMBER_NO, null, null);
            verify(s3Service).delete("members/1/old_260830.jpg");
        }

        @Test
        @DisplayName("이미 기본 프로필이면 IMAGE_ALREADY_DEFAULT, 아무것도 하지 않는다")
        void alreadyDefault() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());

            assertThatThrownBy(() -> memberService.deleteMemberImg(MEMBER_NO))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.IMAGE_ALREADY_DEFAULT);
            verify(memberMapper, never()).updateMemberImg(any(), any(), any());
            verify(s3Service, never()).delete(any());
        }

        @Test
        @DisplayName("회원이 없으면 ENTITY_NOT_FOUND")
        void notFound() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(null);

            assertThatThrownBy(() -> memberService.deleteMemberImg(MEMBER_NO))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("deleteMember (회원 탈퇴)")
    class DeleteMember {

        private final jakarta.servlet.http.HttpServletResponse response =
                mock(jakarta.servlet.http.HttpServletResponse.class);

        @Test
        @DisplayName("비번 일치 시 소프트삭제 + 토큰 전체 폐기 + 쿠키 삭제")
        void success() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(passwordEncoder.matches("qwer1234", ENCODED_PWD)).thenReturn(true);

            memberService.deleteMember(MEMBER_NO, "qwer1234", response);

            verify(memberMapper).updateMemberDelYn(MEMBER_NO);
            verify(tokenMapper).deleteByMemberNo(MEMBER_NO);
            verify(cookieUtil).deleteAuthCookies(response);
        }

        @Test
        @DisplayName("비번 불일치면 PASSWORD_MISMATCH, 아무것도 하지 않는다")
        void passwordMismatch() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(passwordEncoder.matches("wrong", ENCODED_PWD)).thenReturn(false);

            assertThatThrownBy(() -> memberService.deleteMember(MEMBER_NO, "wrong", response))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
            verify(memberMapper, never()).updateMemberDelYn(any());
            verify(tokenMapper, never()).deleteByMemberNo(any());
            verify(cookieUtil, never()).deleteAuthCookies(any());
        }

        @Test
        @DisplayName("회원이 없으면 ENTITY_NOT_FOUND")
        void notFound() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(null);

            assertThatThrownBy(() -> memberService.deleteMember(MEMBER_NO, "qwer1234", response))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
        }
    }
}
