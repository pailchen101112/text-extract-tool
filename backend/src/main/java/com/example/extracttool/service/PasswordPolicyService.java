package com.example.extracttool.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PasswordPolicyService {
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    public void validate(String password, String username) {
        if (password == null || password.length() < 12 || password.length() > 64) {
            throw new IllegalArgumentException("密码长度必须为 12-64 位");
        }
        if (!UPPER.matcher(password).find() || !LOWER.matcher(password).find()
                || !DIGIT.matcher(password).find() || !SPECIAL.matcher(password).find()) {
            throw new IllegalArgumentException("密码必须同时包含大写字母、小写字母、数字和特殊字符");
        }
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            throw new IllegalArgumentException("密码不能包含用户名");
        }
    }
}
