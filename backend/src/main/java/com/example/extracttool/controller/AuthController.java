package com.example.extracttool.controller;

import com.example.extracttool.dto.ChangePasswordRequest;
import com.example.extracttool.dto.LoginRequest;
import com.example.extracttool.service.AuthService;
import com.example.extracttool.service.ClientIpService;
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
    private final ClientIpService clientIpService;
    public AuthController(AuthService authService, ClientIpService clientIpService) {
        this.authService = authService;
        this.clientIpService = clientIpService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, clientIpService.resolve(httpRequest));
    }

    @GetMapping("/me")
    public Map<String, Object> me() { return authService.currentProfile(); }

    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                               HttpServletRequest httpRequest) {
        authService.changePassword(request, clientIpService.resolve(httpRequest));
        return ResponseEntity.ok(Collections.singletonMap("message", "密码修改成功，请重新登录"));
    }
}
