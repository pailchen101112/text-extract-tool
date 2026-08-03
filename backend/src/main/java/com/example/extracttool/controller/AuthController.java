package com.example.extracttool.controller;

import com.example.extracttool.dto.ChangePasswordRequest;
import com.example.extracttool.dto.LoginRequest;
import com.example.extracttool.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, clientIp(httpRequest));
    }

    @GetMapping("/me")
    public Map<String, Object> me() { return authService.currentProfile(); }

    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                               HttpServletRequest httpRequest) {
        authService.changePassword(request, clientIp(httpRequest));
        return ResponseEntity.ok(Collections.singletonMap("message", "密码修改成功，请重新登录"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.trim().isEmpty() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
