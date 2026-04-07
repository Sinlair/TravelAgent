package com.travalagent.types.exception;

import com.travalagent.types.enums.ResponseCode;

public class AppException extends RuntimeException {

    private final String code;

    public AppException(ResponseCode responseCode, String message) {
        super(message);
        this.code = responseCode.getCode();
    }

    public AppException(ResponseCode responseCode, String message, Throwable cause) {
        super(message, cause);
        this.code = responseCode.getCode();
    }

    public String getCode() {
        return code;
    }
}
