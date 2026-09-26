package com.b2binventory.controller;

import com.b2binventory.dto.LoginRequest;
import com.b2binventory.dto.RegisterBusinessRequest;
import com.b2binventory.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-business")
    public Map<String, Object> registerBusiness(@RequestBody RegisterBusinessRequest request) {
        return authService.registerBusiness(request);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
