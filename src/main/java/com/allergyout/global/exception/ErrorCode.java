package com.allergyout.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 묶음 코드 - 구체 문구는 응답 data({필드: 메시지})로 전달
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),   // @Valid 형식 검증 실패 전부
    DUPLICATE_VALUE(HttpStatus.CONFLICT, "이미 사용 중인 값입니다."),           // 이메일/연락처/아이디 등 UNIQUE 위반

    // 개별 코드 - msg 자체가 구체 문구, data 없음
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 달라야 합니다."),
    IMAGE_ALREADY_DEFAULT(HttpStatus.BAD_REQUEST, "이미 기본 프로필 사진입니다."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "파일을 첨부해주세요."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "파일 용량은 5MB를 넘을 수 없습니다."),
    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다. (jpg, jpeg, png 만 가능)"),

    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 레시피입니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "즐겨찾기하지 않은 레시피입니다."),
    ALREADY_BOOKMARKED(HttpStatus.CONFLICT, "이미 즐겨찾기한 레시피입니다."),   // 관계 중복 (UNIQUE 값 충돌 아님 → DUPLICATE_VALUE 와 별개)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
