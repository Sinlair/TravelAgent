package com.xx2201.travel.agent.types.response;

import com.xx2201.travel.agent.types.enums.ResponseCode;

public record ApiResponse<T>(String code, String info, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getInfo(), data);
    }

    public static <T> ApiResponse<T> failure(ResponseCode responseCode, String info) {
        return new ApiResponse<>(responseCode.getCode(), info, null);
    }
}
