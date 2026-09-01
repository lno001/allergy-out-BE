package com.allergyout.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.member.model.dao.MemberMapper;
import com.allergyout.member.model.dto.MemberAllergyResponse;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberMapper memberMapper;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberMapper);
    }

    @Test
    void getAllergyList_정상_알러지목록반환() {
        Long memberNo = 1L;
        when(memberMapper.existsByMemberNo(memberNo)).thenReturn(true);
        when(memberMapper.getAllergyList(memberNo)).thenReturn(List.of("땅콩", "우유", "갑각류"));

        MemberAllergyResponse response = memberService.getAllergyList(memberNo);

        assertThat(response.allergyList()).containsExactly("땅콩", "우유", "갑각류");
    }

    @Test
    void getAllergyList_경계_알러지없으면_빈리스트() {
        Long memberNo = 1L;
        when(memberMapper.existsByMemberNo(memberNo)).thenReturn(true);
        when(memberMapper.getAllergyList(memberNo)).thenReturn(List.of());

        MemberAllergyResponse response = memberService.getAllergyList(memberNo);

        assertThat(response.allergyList()).isEmpty();
    }

    @Test
    void getAllergyList_예외_존재하지않는회원() {
        Long memberNo = 999L;
        when(memberMapper.existsByMemberNo(memberNo)).thenReturn(false);

        assertThatThrownBy(() -> memberService.getAllergyList(memberNo))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }
}
