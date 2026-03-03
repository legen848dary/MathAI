package com.insoftu.mathai.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorksheetRequest(
        @NotNull @Min(6) @Max(12) Integer grade,
        @NotBlank String topic,
        @NotBlank String difficulty,
        int questionCount,
        String context   // optional: free-text keywords / context provided by the user
) {
    public WorksheetRequest {
        if (questionCount <= 0 || questionCount > 20) questionCount = 10;
    }
}

