package com.allergyout.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.allergyout.auth.model.dao.TokenMapper;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.global.security.CookieUtil;
import com.allergyout.member.model.dao.MemberMapper;
import com.allergyout.member.model.dto.MemberAllergyResponse;
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

    private Member memberWithoutImg() {
        return Member.builder()
                .memberNo(MEMBER_NO)
                .memberId("minjai")
                .memberPwd("encoded-current-pwd")
                .memberName("김민재")
                .phone("01012341234")
                .email("allergyout@gmail.com")
                .role("ROLE_USER")
                .memberImg(null)
                .memberImgPath(null)
                .delYn("N")
                .build();
    }

    @Nested
    @DisplayName("getAllergyList")
    class GetAllergyList {

        @Test
        @DisplayName("정상 조회 시 알러지 목록을 반환한다")
        void success() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(memberMapper.getAllergyList(MEMBER_NO)).thenReturn(List.of("땅콩", "우유", "갑각류"));

            MemberAllergyResponse result = memberService.getAllergyList(MEMBER_NO);

            assertThat(result.allergyList()).containsExactly("땅콩", "우유", "갑각류");
        }

        @Test
        @DisplayName("알러지가 없으면 빈 리스트를 반환한다")
        void empty() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(memberWithoutImg());
            when(memberMapper.getAllergyList(MEMBER_NO)).thenReturn(List.of());

            MemberAllergyResponse result = memberService.getAllergyList(MEMBER_NO);

            assertThat(result.allergyList()).isEmpty();
        }

        @Test
        @DisplayName("회원이 없으면 MEMBER_NOT_FOUND")
        void notFound() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(null);

            assertThatThrownBy(() -> memberService.getAllergyList(MEMBER_NO))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }
    }
}
