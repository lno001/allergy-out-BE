package com.allergyout.allergy.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.allergyout.allergy.model.dao.AllergyMapper;
import com.allergyout.allergy.model.dto.AllergyResponse;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.member.model.dao.MemberMapper;
import com.allergyout.member.model.vo.Member;

@ExtendWith(MockitoExtension.class)
class AllergyServiceTest {

    @Mock
    private AllergyMapper allergyMapper;
    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private AllergyService allergyService;

    private static final long MEMBER_NO = 1L;

    private Member member() {
        return Member.builder()
                .memberNo(MEMBER_NO)
                .memberId("minjai")
                .memberPwd("encoded-current-pwd")
                .memberName("김민재")
                .phone("01012341234")
                .email("allergyout@gmail.com")
                .role("ROLE_USER")
                .createDate(LocalDateTime.of(2025, 8, 20, 14, 30, 0))
                .delYn("N")
                .build();
    }

    @Nested
    @DisplayName("getAllergyList")
    class GetAllergyList {

        @Test
        @DisplayName("정상 조회 시 알러지 목록을 반환한다")
        void success() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member());
            when(allergyMapper.getAllergyList(MEMBER_NO)).thenReturn(List.of("땅콩", "우유", "갑각류"));

            AllergyResponse result = allergyService.getAllergyList(MEMBER_NO);

            assertThat(result.allergyList()).containsExactly("땅콩", "우유", "갑각류");
        }

        @Test
        @DisplayName("알러지가 없으면 빈 리스트를 반환한다")
        void empty() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member());
            when(allergyMapper.getAllergyList(MEMBER_NO)).thenReturn(List.of());

            AllergyResponse result = allergyService.getAllergyList(MEMBER_NO);

            assertThat(result.allergyList()).isEmpty();
        }

        @Test
        @DisplayName("회원이 없으면 MEMBER_NOT_FOUND")
        void notFound() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(null);

            assertThatThrownBy(() -> allergyService.getAllergyList(MEMBER_NO))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("updateAllergyList")
    class UpdateAllergyList {

        @Test
        @DisplayName("정상 요청이면 전체 삭제 후 재삽입하고 그대로 반환한다")
        void success() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member());

            AllergyResponse result =
                    allergyService.updateAllergyList(MEMBER_NO, List.of("땅콩", "우유", "갑각류"));

            assertThat(result.allergyList()).containsExactly("땅콩", "우유", "갑각류");
            verify(allergyMapper).deleteAllergyList(MEMBER_NO);
            verify(allergyMapper).insertAllergy(MEMBER_NO, "땅콩");
            verify(allergyMapper).insertAllergy(MEMBER_NO, "우유");
            verify(allergyMapper).insertAllergy(MEMBER_NO, "갑각류");
        }

        @Test
        @DisplayName("빈 리스트를 보내면 삭제만 하고 재삽입은 하지 않는다")
        void emptyList_deletesOnly() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member());

            AllergyResponse result = allergyService.updateAllergyList(MEMBER_NO, List.of());

            assertThat(result.allergyList()).isEmpty();
            verify(allergyMapper).deleteAllergyList(MEMBER_NO);
            verify(allergyMapper, never()).insertAllergy(any(), any());
        }

        @Test
        @DisplayName("100개를 초과하면 INVALID_INPUT_VALUE + allergyList 키 메시지, 삭제/삽입 미호출")
        void tooMany() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member());
            List<String> tooMany = java.util.stream.IntStream.range(0, 101)
                    .mapToObj(i -> "재료" + i)
                    .toList();

            assertThatThrownBy(() -> allergyService.updateAllergyList(MEMBER_NO, tooMany))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                        assertThat(ce.getDetails())
                                .containsExactly(entry("allergyList", "알러지 항목은 최대 100개까지 등록할 수 있습니다."));
                    });
            verify(allergyMapper, never()).deleteAllergyList(any());
            verify(allergyMapper, never()).insertAllergy(any(), any());
        }

        @Test
        @DisplayName("정확히 100개면 통과한다 (경계값)")
        void exactlyMax() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member());
            List<String> exactlyMax = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> "재료" + i)
                    .toList();

            AllergyResponse result = allergyService.updateAllergyList(MEMBER_NO, exactlyMax);

            assertThat(result.allergyList()).hasSize(100);
            verify(allergyMapper).deleteAllergyList(MEMBER_NO);
        }

        @Test
        @DisplayName("항목이 30자를 초과하면 INVALID_INPUT_VALUE + 인덱스 필드 메시지, 삭제/삽입 미호출")
        void tooLong() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member());
            String tooLong = "a".repeat(31);

            assertThatThrownBy(() -> allergyService.updateAllergyList(MEMBER_NO, List.of("땅콩", tooLong)))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                        assertThat(ce.getDetails())
                                .containsExactly(entry("allergyList[1]", "알러지 항목은 각각 30자 이내로 입력해주세요."));
                    });
            verify(allergyMapper, never()).deleteAllergyList(any());
            verify(allergyMapper, never()).insertAllergy(any(), any());
        }

        @Test
        @DisplayName("빈 문자열 항목이 있으면 INVALID_INPUT_VALUE + 인덱스 필드 메시지")
        void blankItem() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member());

            assertThatThrownBy(() -> allergyService.updateAllergyList(MEMBER_NO, List.of("땅콩", " ")))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                        assertThat(ce.getDetails())
                                .containsExactly(entry("allergyList[1]", "알러지 항목은 비어있을 수 없습니다."));
                    });
        }

        @Test
        @DisplayName("같은 항목이 중복되면 INVALID_INPUT_VALUE + 인덱스 필드 메시지")
        void duplicated() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member());

            assertThatThrownBy(() -> allergyService.updateAllergyList(MEMBER_NO, List.of("땅콩", "우유", "땅콩")))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                        assertThat(ce.getDetails())
                                .containsExactly(entry("allergyList[2]", "중복된 알러지 항목입니다."));
                    });
        }

        @Test
        @DisplayName("여러 항목이 동시에 잘못되면 전부 data에 담긴다")
        void multipleErrors() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(member());

            assertThatThrownBy(() -> allergyService.updateAllergyList(MEMBER_NO, List.of("a".repeat(31), "우유", " ")))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getDetails()).containsExactly(
                                entry("allergyList[0]", "알러지 항목은 각각 30자 이내로 입력해주세요."),
                                entry("allergyList[2]", "알러지 항목은 비어있을 수 없습니다."));
                    });
        }

        @Test
        @DisplayName("회원이 없으면 MEMBER_NOT_FOUND, 삭제/삽입 미호출")
        void notFound() {
            when(memberMapper.getMember(MEMBER_NO)).thenReturn(null);

            assertThatThrownBy(() -> allergyService.updateAllergyList(MEMBER_NO, List.of("땅콩")))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
            verify(allergyMapper, never()).deleteAllergyList(any());
            verify(allergyMapper, never()).insertAllergy(any(), any());
        }
    }
}
