package com.insoftu.mathai.ai;

/**
 * Wraps an AI provider's raw response.
 */
public record AiResponse(String text, boolean truncated) {
}
