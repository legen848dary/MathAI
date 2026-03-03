package com.insoftu.mathai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insoftu.mathai.model.WorksheetRequest;
import com.insoftu.mathai.model.WorksheetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String model;

    public GeminiService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public WorksheetResponse generateWorksheet(WorksheetRequest request) {
        String prompt = buildPrompt(request);
        GeminiResult result = callGemini(prompt);

        // If Gemini hit the token limit, retry once with fewer questions
        if (result.truncated()) {
            int reducedCount = Math.max(3, request.questionCount() - 2);
            log.warn("Gemini response was truncated (MAX_TOKENS). Retrying with {} questions instead of {}.",
                    reducedCount, request.questionCount());
            WorksheetRequest reduced = new WorksheetRequest(
                    request.grade(), request.topic(), request.difficulty(), reducedCount, request.context());
            result = callGemini(buildPrompt(reduced));
            if (result.truncated()) {
                throw new RuntimeException(
                    "Gemini response was truncated even after reducing question count. " +
                    "Please try again with fewer questions or a simpler topic.");
            }
        }

        return parseResponse(result.text(), request);
    }

    private String buildPrompt(WorksheetRequest request) {
        String contextLine = (request.context() != null && !request.context().isBlank())
                ? "- Additional context / focus keywords from the teacher: " + request.context().strip()
                : "";

        return """
                You are an IB mathematics teacher. Generate a worksheet as a single valid JSON object.

                Specs:
                - Programme: %s (grade %d)
                - Topic: %s
                - Difficulty: %s
                - Questions: %d
                %s

                Rules:
                - Questions must align to IB MYP/DP standards and progress from easier to harder.
                - Use plain-text math notation (x^2, sqrt(x), pi, etc.).
                - Keep each answer concise: show key steps only, not an essay.
                - If additional context/keywords are provided above, tailor the questions to reflect them.
                - Respond with RAW JSON only — no markdown, no code fences.

                DIAGRAMS:
                - For any question that involves geometry, shapes, graphs, coordinate planes, angles,
                  number lines, or any concept that is clearer with a visual aid, you MUST include a
                  diagram as a self-contained inline SVG string in the "diagram" field.
                - The SVG must have width="300" height="220" and use only basic SVG elements
                  (line, circle, rect, polygon, polyline, path, text, g).
                - Use stroke="#1e3a5f" fill="none" for lines/shapes, fill="#1e3a5f" for text labels.
                - The SVG must be a single-line string (no real newlines inside the JSON value).
                - If no diagram is needed for a question, omit the "diagram" field entirely.

                Required JSON format:
                {
                  "title": "string",
                  "instructions": "string",
                  "questions": [
                    { "number": 1, "text": "string", "hint": "string", "diagram": "<svg ...>...</svg>" }
                  ],
                  "answerKey": [
                    "1. concise worked answer"
                  ]
                }
                """.formatted(
                        request.grade() >= 11 ? "DP" : "MYP",
                        request.grade(),
                        request.topic(),
                        request.difficulty(),
                        request.questionCount(),
                        contextLine);
    }

    private record GeminiResult(String text, boolean truncated) {}

    private GeminiResult callGemini(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 16384
                )
        );

        String url = "/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        try {
            String response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            log.debug("Gemini raw response: {}", response);

            // Check if Gemini stopped due to token limit
            boolean truncated = false;
            try {
                JsonNode root = objectMapper.readTree(response);
                String finishReason = root.path("candidates").get(0).path("finishReason").asText("");
                if ("MAX_TOKENS".equals(finishReason)) {
                    truncated = true;
                    log.warn("Gemini finishReason=MAX_TOKENS — response was cut off.");
                }
            } catch (Exception ignored) {}

            return new GeminiResult(extractTextFromGeminiResponse(response), truncated);
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("Gemini API error {}: {}", e.getStatusCode(), body);
            if (e.getStatusCode().value() == 429) {
                throw new RuntimeException(
                    "Gemini API quota exceeded. Please wait a moment and try again, " +
                    "or check your quota at https://ai.google.dev/gemini-api/docs/rate-limits");
            } else if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new RuntimeException(
                    "Gemini API key is invalid or unauthorized. " +
                    "Please check your GEMINI_API_KEY at https://aistudio.google.com");
            }
            throw new RuntimeException("Gemini API error " + e.getStatusCode() + ": " + body);
        }
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
                String diagram = q.hasNonNull("diagram") ? q.path("diagram").asText(null) : null;
                questions.add(new WorksheetResponse.Question(
                        q.path("number").asInt(questions.size() + 1),
                        q.path("text").asText(),
                        q.path("hint").asText(""),
                        diagram
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

