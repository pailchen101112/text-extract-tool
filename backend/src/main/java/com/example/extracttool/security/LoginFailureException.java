package com.example.extracttool.security;

public class LoginFailureException extends RuntimeException {
    public LoginFailureException(String message) { super(message); }
}
