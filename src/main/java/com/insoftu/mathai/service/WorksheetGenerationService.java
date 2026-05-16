package com.insoftu.mathai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insoftu.mathai.ai.AiProviderManager;
import com.insoftu.mathai.ai.AiResponse;
import com.insoftu.mathai.ai.AiServiceException;
import com.insoftu.mathai.model.WorksheetRequest;
import com.insoftu.mathai.model.WorksheetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WorksheetGenerationService {

    private static final Logger log = LoggerFactory.getLogger(WorksheetGenerationService.class);

    private final AiProviderManager aiProviderManager;
    private final ObjectMapper objectMapper;

    public WorksheetGenerationService(AiProviderManager aiProviderManager, ObjectMapper objectMapper) {
        this.aiProviderManager = aiProviderManager;
        this.objectMapper = objectMapper;
    }

    public WorksheetResponse generateWorksheet(WorksheetRequest request) {
        var aiService = aiProviderManager.getCurrentProvider();
        log.info("Worksheet request — provider={}, grade={}, topic='{}', difficulty={}, questions={}",
                aiService.getProviderName(), request.grade(), request.topic(),
                request.difficulty(), request.questionCount());

        long startMs = System.currentTimeMillis();

        String systemPrompt = "You are an IB mathematics teacher. Generate a worksheet as a single valid JSON object.";
        String userPrompt = buildUserPrompt(request);
        int maxTokens = 16384;

        AiResponse aiResponse = aiService.generateContent(systemPrompt, userPrompt, maxTokens, 0.7);

        // Retry with fewer questions if truncated
        if (aiResponse.truncated()) {
            int reducedCount = Math.max(3, request.questionCount() - 2);
            log.warn("AI response truncated. Retrying with {} questions instead of {}.",
                    reducedCount, request.questionCount());
            WorksheetRequest reduced = new WorksheetRequest(
                    request.grade(), request.topic(), request.difficulty(), reducedCount, request.context());
            aiResponse = aiService.generateContent(systemPrompt, buildUserPrompt(reduced), maxTokens, 0.7);
            if (aiResponse.truncated()) {
                throw new AiServiceException(
                    "Response was truncated even after reducing question count. " +
                    "Please try again with fewer questions or a simpler topic.");
            }
        }

        WorksheetResponse response = parseResponse(aiResponse.text(), request);

        long elapsedMs = System.currentTimeMillis() - startMs;
        log.info("Worksheet generated — provider={}, grade={}, topic='{}', questions={}, elapsed={}ms",
                aiService.getProviderName(), request.grade(), request.topic(),
                response.questions().size(), elapsedMs);

        return response;
    }

    private String buildUserPrompt(WorksheetRequest request) {
        String contextLine = (request.context() != null && !request.context().isBlank())
                ? "- Additional context / focus keywords from the teacher: " + request.context().strip()
                : "";

        return """
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

    private WorksheetResponse parseResponse(String jsonText, WorksheetRequest request) {
        try {
            String cleaned = jsonText.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }

            cleaned = sanitiseJsonControlChars(cleaned);

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
            throw new AiServiceException("Failed to parse worksheet JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Walks a raw JSON string and replaces literal control characters
     * (newline, carriage-return, tab, and other chars 0x00-0x1F) that appear
     * inside JSON string values with their proper JSON escape sequences.
     * Characters outside string values (structural JSON) are left untouched.
     */
    private static String sanitiseJsonControlChars(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 64);
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                sb.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (inString && c < 0x20) {
                switch (c) {
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> sb.append(String.format("\\u%04x", (int) c));
                }
                continue;
            }

            sb.append(c);
        }
        return sb.toString();
    }
}
