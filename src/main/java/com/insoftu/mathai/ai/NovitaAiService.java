package com.insoftu.mathai.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NovitaAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(NovitaAiService.class);

    @Value("${novita.api.key:}")
    private String apiKey;

    @Value("${novita.api.model:deepseek/deepseek-v4-flash}")
    private String model;

    @Value("${novita.api.base-url:https://api.novita.ai/openai}")
    private String baseUrl;

    @Override
    public AiResponse generateContent(String systemMessage, String userMessage, int maxTokens, double temperature)
            throws AiServiceException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException(
                "Novita API key is not configured. Set NOVITA_API_KEY environment variable.");
        }

        log.debug("Novita request — model={}, maxTokens={}, temp={}", model, maxTokens, temperature);

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addMessage(ChatCompletionSystemMessageParam.builder()
                        .content(systemMessage).build())
                .addMessage(ChatCompletionUserMessageParam.builder()
                        .content(userMessage).build())
                .model(ChatModel.of(model))
                .maxTokens((long) maxTokens)
                .temperature(temperature)
                .build();

        try {
            ChatCompletion completion = client.chat().completions().create(params);

            ChatCompletionMessage message = completion.choices().get(0).message();
            String text = message.content().orElse("");

            String finishReason = completion.choices().get(0).finishReason().toString();
            boolean truncated = "LENGTH".equals(finishReason);

            if (truncated) {
                log.warn("Novita finish_reason='length' — response was cut off.");
            }

            return new AiResponse(text, truncated);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Novita API error: {}", e.getMessage(), e);
            String msg = e.getMessage();
            if (msg != null) {
                if (msg.contains("401") || msg.contains("403")) {
                    throw new AiServiceException(401,
                        "Novita API key is invalid. Check NOVITA_API_KEY at https://novita.ai");
                }
                if (msg.contains("429")) {
                    throw new AiServiceException(429,
                        "Novita API rate limit exceeded. Please wait and try again.");
                }
            }
            throw new AiServiceException("Novita API error: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "novita";
    }
}
