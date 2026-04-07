package com.travalagent.types.response;

import com.travalagent.types.enums.ResponseCode;

public record ApiResponse<T>(String code, String info, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getInfo(), data);
    }

    public static <T> ApiResponse<T> failure(ResponseCode responseCode, String info) {
        return new ApiResponse<>(responseCode.getCode(), info, null);
    }
}
