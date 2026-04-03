package com.xx2201.travel.agent.types.exception;

import com.xx2201.travel.agent.types.enums.ResponseCode;

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
