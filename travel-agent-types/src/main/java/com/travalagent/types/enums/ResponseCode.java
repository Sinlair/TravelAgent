package com.travalagent.types.enums;

public enum ResponseCode {
    SUCCESS("0000", "success"),
    INVALID_REQUEST("1001", "invalid request"),
    SYSTEM_ERROR("9999", "system error");

    private final String code;
    private final String info;

    ResponseCode(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }
}
