package com.allergyout.member.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.allergyout.auth.model.dto.SignupRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * 회원 DTO 형식 검증(@Pattern/@Email/@Size/@NotNull) 경계값 잠금 테스트.
 * FE 와 규칙이 어긋나거나 정규식이 바뀌면 여기서 깨진다.
 * SignupRequest(auth) 와 Member*UpdateRequest(member) 가 같은 규칙을 쓰는지도 확인한다.
 */
@DisplayName("회원 DTO 형식 검증 규칙")
class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    // 특정 필드에 대한 위반 메시지만 추린다.
    private static List<String> messages(Object dto, String property) {
        return validator.validate(dto).stream()
                .filter(v -> v.getPropertyPath().toString().equals(property))
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    // 유효한 SignupRequest 에서 한 필드만 교체한다.
    private static SignupRequest signupWith(String property, String value) {
        String id = "user01", pwd = "abcd1234", name = "김민준", phone = "01012345678", email = "user@example.com";
        return switch (property) {
            case "memberId" -> new SignupRequest(value, pwd, name, phone, email);
            case "memberPwd" -> new SignupRequest(id, value, name, phone, email);
            case "memberName" -> new SignupRequest(id, pwd, value, phone, email);
            case "phone" -> new SignupRequest(id, pwd, name, value, email);
            case "email" -> new SignupRequest(id, pwd, name, phone, value);
            default -> throw new IllegalArgumentException(property);
        };
    }

    private static void accepts(String property, String... values) {
        for (String v : values) {
            assertThat(messages(signupWith(property, v), property))
                    .as("%s = \"%s\" 는 통과해야 함", property, v)
                    .isEmpty();
        }
    }

    private static void rejects(String property, String... values) {
        for (String v : values) {
            assertThat(messages(signupWith(property, v), property))
                    .as("%s = \"%s\" 는 거부돼야 함", property, v)
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("memberId — ^[a-z0-9]{4,20}$")
    class MemberId {
        @Test
        void accepts_boundary() {
            accepts("memberId", "abcd", "user01", "a".repeat(20));
        }

        @Test
        void rejects_invalid() {
            rejects("memberId",
                    "abc",           // 3자
                    "a".repeat(21),  // 21자
                    "Abcd",          // 대문자
                    "user!@#",       // 특수문자
                    "user 01",       // 공백
                    "유저01",        // 한글
                    "user😀", // 이모지
                    "");             // 빈 값
        }
    }

    @Nested
    @DisplayName("memberPwd — 영문+숫자 필수, 특수문자 선택, 공백 불가, 8~30")
    class MemberPwd {
        @Test
        void accepts_boundary() {
            accepts("memberPwd",
                    "abcd1234",              // 8자, 특수문자 없음
                    "Passw0rd!@#$%^&*()-_=", // 특수문자 포함
                    "a1" + "b".repeat(28));  // 30자
        }

        @Test
        void rejects_invalid() {
            rejects("memberPwd",
                    "abcdefgh",             // 숫자 없음
                    "12345678",             // 영문 없음
                    "abcd123",              // 7자
                    "a1" + "b".repeat(29),  // 31자
                    "abcd 1234",            // 공백
                    "abcd1234가",           // 한글(비 ASCII)
                    "");                    // 빈 값
        }
    }

    @Nested
    @DisplayName("memberName — ^[가-힣A-Za-z]{2,30}$")
    class MemberName {
        @Test
        void accepts_boundary() {
            accepts("memberName", "김민준", "Ab", "김Anna", "가".repeat(30));
        }

        @Test
        void rejects_invalid() {
            rejects("memberName",
                    "김",            // 1자
                    "가".repeat(31), // 31자
                    "홍 길동",       // 공백
                    "John Smith",   // 공백
                    "길동2",         // 숫자
                    "ㄱㄴ",          // 자모
                    "김!",           // 기호
                    "");            // 빈 값
        }
    }

    @Nested
    @DisplayName("phone — ^010[0-9]{8}$")
    class Phone {
        @Test
        void accepts_boundary() {
            accepts("phone", "01012345678");
        }

        @Test
        void rejects_invalid() {
            rejects("phone",
                    "0111234567",     // 011
                    "0101234567",     // 10자리
                    "010123456789",   // 12자리
                    "0102222-3333",   // 하이픈
                    "010-1234-5678",  // 하이픈
                    "0101234567a",    // 문자
                    "");              // 빈 값
        }
    }

    @Nested
    @DisplayName("email — ASCII 형식만, TLD 2~24, 한글 불가")
    class Email {
        @Test
        void accepts_boundary() {
            accepts("email", "user@example.com", "a.b+tag@sub.example.co", "u_1%x@a-b.io");
        }

        @Test
        void rejects_invalid() {
            rejects("email",
                    "a@b",                            // TLD 없음
                    "a@b.c",                          // TLD 1자
                    "홍길동@gmail.com",               // 한글
                    "a@@b.com",                       // @ 2개
                    "noatsign.com",                   // @ 없음
                    "a b@example.com",                // 공백
                    "a".repeat(40) + "@example.com",  // 52자 → @Size(50) 초과
                    "");                              // 빈 값
        }
    }

    @Nested
    @DisplayName("blank/null 위반 메시지 결정성")
    class BlankMessage {
        @Test
        @DisplayName("null 이면 '입력해주세요' 하나만 나온다")
        void nullGivesPresenceMessageOnly() {
            SignupRequest r = new SignupRequest(null, null, null, null, null);
            assertThat(messages(r, "memberId")).containsExactly("아이디를 입력해주세요.");
            assertThat(messages(r, "memberPwd")).containsExactly("비밀번호를 입력해주세요.");
            assertThat(messages(r, "memberName")).containsExactly("이름을 입력해주세요.");
            assertThat(messages(r, "phone")).containsExactly("연락처를 입력해주세요.");
            assertThat(messages(r, "email")).containsExactly("이메일을 입력해주세요.");
        }

        @Test
        @DisplayName("빈 문자열이면 위반이 필드당 하나만 나온다")
        void emptyGivesSingleViolation() {
            SignupRequest r = new SignupRequest("", "", "", "", "valid@example.com");
            assertThat(messages(r, "memberId")).hasSize(1);
            assertThat(messages(r, "memberPwd")).hasSize(1);
            assertThat(messages(r, "memberName")).hasSize(1);
            assertThat(messages(r, "phone")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("member 수정 DTO 는 SignupRequest 와 같은 규칙을 쓴다")
    class MemberUpdateDtosMatch {
        @Test
        void name() {
            assertThat(messages(new MemberNameUpdateRequest("홍 길동"), "memberName")).isNotEmpty();
            assertThat(messages(new MemberNameUpdateRequest("김민준"), "memberName")).isEmpty();
            assertThat(messages(new MemberNameUpdateRequest(null), "memberName"))
                    .containsExactly("이름을 입력해주세요.");
        }

        @Test
        void email() {
            assertThat(messages(new MemberEmailUpdateRequest("홍길동@gmail.com"), "email")).isNotEmpty();
            assertThat(messages(new MemberEmailUpdateRequest("a@b.c"), "email")).isNotEmpty();
            assertThat(messages(new MemberEmailUpdateRequest("user@example.com"), "email")).isEmpty();
        }

        @Test
        void newPassword() {
            assertThat(messages(new MemberPwdUpdateRequest("current-pw", "abcdefgh"), "newPassword")).isNotEmpty();
            assertThat(messages(new MemberPwdUpdateRequest("current-pw", "abcd1234"), "newPassword")).isEmpty();
            assertThat(messages(new MemberPwdUpdateRequest("current-pw", null), "newPassword"))
                    .containsExactly("새 비밀번호를 입력해주세요.");
        }

        @Test
        void phone() {
            assertThat(messages(new MemberPhoneUpdateRequest("0102222-3333"), "phone")).isNotEmpty();
            assertThat(messages(new MemberPhoneUpdateRequest("01012345678"), "phone")).isEmpty();
        }
    }
}
