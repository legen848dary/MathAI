package com.insoftu.mathai.config;

import com.insoftu.mathai.ai.AiServiceException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * AI service errors — e.g. API key invalid, rate limited.
     */
    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAiService(AiServiceException ex) {
        int status = ex.getStatusCode() != 0 ? ex.getStatusCode() : 500;
        log.error("AI service error ({}): {}", status, ex.getMessage());
        return ResponseEntity.status(status).body(Map.of(
                "status", status,
                "error", "AI Service Error",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * 404 — unknown paths (bot probes, typos, etc.).
     * Logged at WARN (no stack trace) to avoid polluting error logs.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
        log.warn("404 Not Found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "error", "Not Found",
                "message", ex.getMessage() != null ? ex.getMessage() : "Resource not found",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Client disconnected before response could be sent — harmless, log at WARN.
     * Common with long-running AI generation where the browser/proxy times out.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
        if (ex.getCause() instanceof ClientAbortException) {
            log.warn("Client disconnected before response completed: {}", ex.getCause().getMessage());
        } else {
            log.warn("Async request not usable: {}", ex.getMessage());
        }
    }

    /**
     * Catch-all for genuine application errors.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "message", ex.getMessage() != null ? ex.getMessage() : "Unknown error",
                "timestamp", Instant.now().toString()
        ));
    }
}

