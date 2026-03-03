package com.insoftu.mathai.model;

import java.util.List;

public record WorksheetResponse(
        String title,
        String grade,
        String topic,
        String difficulty,
        String instructions,
        List<Question> questions,
        List<String> answerKey
) {
    public record Question(
            int number,
            String text,
            String hint,
            String diagram   // optional inline SVG string; null if no diagram needed
    ) {}
}

