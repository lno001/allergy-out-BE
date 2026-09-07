package com.allergyout.auth.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 형식 검증만 담당한다(중복·본인인증은 Service).
 * 규칙은 FE 와 반드시 동일해야 하며 RequestValidationTest 로 잠근다.
 *
 * 어노테이션 조합 규칙:
 *  - @Pattern 필드 → @NotNull + @Pattern  (blank 입력 시 위반이 하나만 나오도록. @Pattern 은 null 을 통과시킴)
 *  - @Email  필드 → @NotBlank + @Email    (@Email·@Size 는 "" 를 통과시키므로 공백은 @NotBlank 가 잡음)
 */
public record SignupRequest(

        // MEMBER.MEMBER_ID NVARCHAR2(20) · 영문 소문자+숫자 4~20자 (대문자·기호·공백 불가)
        @NotNull(message = "아이디를 입력해주세요.")
        @Pattern(
                regexp = "^[a-z0-9]{4,20}$",
                message = "아이디는 영문 소문자, 숫자로 4자 이상 20자 이하로 입력해주세요."
        )
        String memberId,

        // MEMBER.MEMBER_PWD VARCHAR2(200) · 해시 저장이라 컬럼 길이와 무관
        // 영문+숫자 필수, 특수문자 선택, 공백 불가, 8~30자
        @NotNull(message = "비밀번호를 입력해주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,30}$",
                message = "비밀번호는 영문, 숫자를 포함하여 8자 이상 30자 이하로 입력해주세요. (특수문자 사용 가능, 공백 불가)"
        )
        String memberPwd,

        // MEMBER.MEMBER_NAME NVARCHAR2(30) · 한글(완성형)+영문 2~30자 (공백·숫자·기호 불가)
        @NotNull(message = "이름을 입력해주세요.")
        @Pattern(
                regexp = "^[가-힣A-Za-z]{2,30}$",
                message = "이름은 공백 없이 한글, 영문 2자 이상 30자 이하로 입력해주세요."
        )
        String memberName,

        // MEMBER.PHONE VARCHAR2(20) · 010 + 숫자 8자리 (하이픈 없이 숫자만, FE 에서 정규화)
        @NotNull(message = "연락처를 입력해주세요.")
        @Pattern(
                regexp = "^010[0-9]{8}$",
                message = "올바른 연락처 형식이 아닙니다."
        )
        String phone,

        // MEMBER.EMAIL VARCHAR2(50) · ASCII 형식만 (@ 1개 + TLD 2~24자, 한글 불가)
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(
                regexp = "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,255}\\.[A-Za-z]{2,24}$",
                message = "올바른 이메일 형식이 아닙니다."
        )
        @Size(max = 50, message = "올바른 이메일 형식이 아닙니다.")
        String email
) {
}
