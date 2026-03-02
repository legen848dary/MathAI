package com.insoftu.mathai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insoftu.mathai.model.WorksheetRequest;
import com.insoftu.mathai.model.WorksheetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String MODEL = "gemini-2.0-flash";

    public GeminiService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public WorksheetResponse generateWorksheet(WorksheetRequest request) {
        String prompt = buildPrompt(request);
        String rawJson = callGemini(prompt);
        return parseResponse(rawJson, request);
    }

    private String buildPrompt(WorksheetRequest request) {
        return """
                You are an expert IB (International Baccalaureate) mathematics teacher creating high-quality worksheets.
                
                Generate a math worksheet with the following specifications:
                - IB Curriculum: MYP (Middle Years Programme) for grades 6-10, DP (Diploma Programme) for grades 11-12
                - Grade: %d
                - Topic: %s
                - Difficulty: %s
                - Number of questions: %d
                
                IMPORTANT RULES:
                1. Questions must be age-appropriate and aligned to IB MYP/DP mathematics standards
                2. Questions should progress from easier to harder within the difficulty level
                3. Each question must be clear, unambiguous, and solvable
                4. Provide a brief hint for each question (not the answer)
                5. Provide complete, worked answers in the answer key
                6. Use proper mathematical notation written in plain text (e.g., x^2 for x squared, sqrt(x) for square root)
                
                Respond ONLY with a valid JSON object in this exact format, no markdown, no code blocks, just raw JSON:
                {
                  "title": "worksheet title",
                  "instructions": "brief instructions for the student",
                  "questions": [
                    {
                      "number": 1,
                      "text": "question text here",
                      "hint": "hint text here"
                    }
                  ],
                  "answerKey": [
                    "1. full worked answer here"
                  ]
                }
                """.formatted(request.grade(), request.topic(), request.difficulty(), request.questionCount());
    }

    private String callGemini(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 4096
                )
        );

        String url = "/v1beta/models/" + MODEL + ":generateContent?key=" + apiKey;

        String response = restClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        log.debug("Gemini raw response: {}", response);
        return extractTextFromGeminiResponse(response);
    }

    private String extractTextFromGeminiResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    private WorksheetResponse parseResponse(String jsonText, WorksheetRequest request) {
        try {
            // Strip markdown code fences if Gemini adds them despite instructions
            String cleaned = jsonText.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }

            JsonNode node = objectMapper.readTree(cleaned);

            String title = node.path("title").asText("IB Math Worksheet");
            String instructions = node.path("instructions").asText("Answer all questions. Show your working.");

            List<WorksheetResponse.Question> questions = new ArrayList<>();
            JsonNode questionsNode = node.path("questions");
            for (JsonNode q : questionsNode) {
                questions.add(new WorksheetResponse.Question(
                        q.path("number").asInt(questions.size() + 1),
                        q.path("text").asText(),
                        q.path("hint").asText("")
                ));
            }

            List<String> answerKey = new ArrayList<>();
            JsonNode answersNode = node.path("answerKey");
            for (JsonNode a : answersNode) {
                answerKey.add(a.asText());
            }

            return new WorksheetResponse(
                    title,
                    "Grade " + request.grade(),
                    request.topic(),
                    request.difficulty(),
                    instructions,
                    questions,
                    answerKey
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse worksheet JSON from Gemini: " + e.getMessage(), e);
        }
    }
}

