package com.xx2201.travel.agent.app.controller;

import com.xx2201.travel.agent.types.enums.ResponseCode;
import com.xx2201.travel.agent.types.exception.AppException;
import com.xx2201.travel.agent.types.response.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ApiResponse<Void> handleAppException(AppException exception) {
        return new ApiResponse<>(exception.getCode(), exception.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        return ApiResponse.failure(ResponseCode.SYSTEM_ERROR, exception.getMessage());
    }
}
