package com.travalagent.app.controller;

import com.travalagent.types.enums.ResponseCode;
import com.travalagent.types.exception.AppException;
import com.travalagent.types.response.ApiResponse;
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
