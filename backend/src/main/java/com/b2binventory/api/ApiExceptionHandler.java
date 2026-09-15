package com.b2binventory.api;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(Exception.class)
    public Map<String,Object> handle(Exception e) {
        return Map.of("success", false, "message",
                e.getMessage() == null ? "Something went wrong." : e.getMessage());
    }
}
