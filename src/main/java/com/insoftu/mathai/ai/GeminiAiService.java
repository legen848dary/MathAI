package com.insoftu.mathai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GeminiAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiService.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String model;

    public GeminiAiService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiResponse generateContent(String systemMessage, String userMessage, int maxTokens, double temperature)
            throws AiServiceException {
        log.debug("Gemini request — model={}, maxTokens={}, temp={}", model, maxTokens, temperature);

        String combined = systemMessage + "\n\n" + userMessage;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", combined)))
                ),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "maxOutputTokens", maxTokens
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

            JsonNode root = objectMapper.readTree(response);

            String text = root.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

            String finishReason = root.path("candidates").get(0).path("finishReason").asText("");
            boolean truncated = "MAX_TOKENS".equals(finishReason);

            if (truncated) {
                log.warn("Gemini finishReason=MAX_TOKENS — response was cut off.");
            }

            return new AiResponse(text, truncated);
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("Gemini API error {}: {}", e.getStatusCode(), body);
            int status = e.getStatusCode().value();
            if (status == 429) {
                throw new AiServiceException(status,
                    "Gemini API quota exceeded. Please wait and try again, " +
                    "or check your quota at https://ai.google.dev/gemini-api/docs/rate-limits");
            } else if (status == 401 || status == 403) {
                throw new AiServiceException(status,
                    "Gemini API key is invalid. Please check your GEMINI_API_KEY at https://aistudio.google.com");
            }
            throw new AiServiceException(status, "Gemini API error: " + body);
        } catch (Exception e) {
            if (e instanceof AiServiceException) throw (AiServiceException) e;
            throw new AiServiceException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }
}
