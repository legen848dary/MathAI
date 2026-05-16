package com.insoftu.mathai.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TwoFactorRequest(
        String email,
        String code,
        @JsonProperty("temp_token") String tempToken
) {}
