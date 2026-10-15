package com.b2binventory.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp
) {
    public static ErrorResponse of(int status, String code, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, code, message, fieldErrors, Instant.now());
    }
}
