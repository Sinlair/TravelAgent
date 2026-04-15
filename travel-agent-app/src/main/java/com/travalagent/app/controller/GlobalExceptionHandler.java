package com.travalagent.app.controller;

import com.travalagent.types.enums.ResponseCode;
import com.travalagent.types.exception.AppException;
import com.travalagent.types.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ApiResponse<Void> handleAppException(AppException exception) {
        return new ApiResponse<>(exception.getCode(), exception.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("Unhandled exception while serving API request", exception);
        return ApiResponse.failure(ResponseCode.SYSTEM_ERROR, exception.getMessage());
    }
}
