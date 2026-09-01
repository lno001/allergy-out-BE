package com.allergyout.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> ApiResponse<T> success(String msg, T data) {
        return new ApiResponse<>(200, msg, data);
    }

    public static <T> ApiResponse<T> created(String msg, T data) {
        return new ApiResponse<>(201, msg, data);
    }

    public static <T> ApiResponse<T> fail(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
   
    public static <T> ApiResponse<T> fail(int code, String msg, T data) {
        return new ApiResponse<>(code, msg, data);
    }
}
