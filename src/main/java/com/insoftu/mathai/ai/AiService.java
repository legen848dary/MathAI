package com.insoftu.mathai.ai;

public interface AiService {
    /**
     * Generates content from the AI provider.
     *
     * @param systemMessage  the system-level instruction (e.g. "You are an IB math teacher")
     * @param userMessage    the user prompt with worksheet specifications
     * @param maxTokens      maximum output tokens
     * @param temperature    creativity level (0.0–1.0)
     * @return the response containing raw text and truncation flag
     * @throws AiServiceException on API errors, auth failures, or rate limits
     */
    AiResponse generateContent(String systemMessage, String userMessage, int maxTokens, double temperature)
            throws AiServiceException;

    /** Returns a human-readable provider name (e.g. "gemini", "novita"). */
    String getProviderName();
}
