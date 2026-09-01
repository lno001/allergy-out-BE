package com.allergyout.global.exception;

import java.util.Map;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, String> details; // {필드명: 구체 메시지}, 없으면 null

    public CustomException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public CustomException(ErrorCode errorCode, Map<String, String> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }
}
