package com.insoftu.mathai.admin.dto;

public record LoginResponse(
        String token,
        boolean requiresTwoFactor,
        String message
) {}
