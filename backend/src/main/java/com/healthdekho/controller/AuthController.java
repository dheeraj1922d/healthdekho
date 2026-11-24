package com.healthdekho.controller;

import com.healthdekho.dto.ApiDtos.CustomerDto;
import com.healthdekho.dto.ApiDtos.LoginRequest;
import com.healthdekho.dto.ApiDtos.LoginResponse;
import com.healthdekho.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }

    @GetMapping("/me")
    public CustomerDto me(@AuthenticationPrincipal Jwt jwt) { return authService.me(jwt.getSubject()); }
}
