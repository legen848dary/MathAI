package com.insoftu.mathai.analytics.service;

import com.insoftu.mathai.analytics.model.WorksheetGeneration;
import com.insoftu.mathai.analytics.repository.WorksheetGenerationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final WorksheetGenerationRepository repo;

    public AnalyticsService(WorksheetGenerationRepository repo) {
        this.repo = repo;
    }

    /** Persist a generation result asynchronously so it never blocks the response. */
    @Async
    public void recordGeneration(String provider, Integer grade, String topic, String difficulty,
                                  Integer questionCount, Boolean success, Long elapsedMs,
                                  String errorType, Integer batchCount) {
        try {
            WorksheetGeneration g = new WorksheetGeneration(
                    provider, grade, topic, difficulty, questionCount, success, elapsedMs, errorType, batchCount);
            repo.save(g);
        } catch (Exception e) {
            log.warn("Failed to persist generation record: {}", e.getMessage());
        }
    }

    public Map<String, Object> getOverview() {
        Instant todayStart = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekStart = todayStart.minusSeconds(7 * 86400);

        Map<String, Object> result = new LinkedHashMap<>();

        // --- Usage volume ---
        long totalAllTime = repo.count();
        long totalToday = repo.countByCreatedAtAfter(todayStart);
        long succeededToday = repo.countBySuccessTrueAndCreatedAtAfter(todayStart);
        result.put("totalAllTime", totalAllTime);
        result.put("totalToday", totalToday);

        // --- Success rate ---
        double successRate = totalToday > 0 ? (double) succeededToday / totalToday * 100.0 : 100.0;
        result.put("successRate", Math.round(successRate * 10) / 10.0);
        result.put("succeededToday", succeededToday);
        result.put("failedToday", totalToday - succeededToday);

        // --- Generation time (p50, p95, avg) ---
        List<Long> allTimes = repo.findAllElapsedMsSince(weekStart);
        if (!allTimes.isEmpty()) {
            result.put("avgTimeMs", allTimes.stream().mapToLong(Long::longValue).average().orElse(0));
            result.put("avgTimeSec", Math.round(allTimes.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0 * 10) / 10.0);
            result.put("p50Ms", percentile(allTimes, 50));
            result.put("p95Ms", percentile(allTimes, 95));
            result.put("p50Sec", Math.round(percentile(allTimes, 50) / 1000.0 * 10) / 10.0);
            result.put("p95Sec", Math.round(percentile(allTimes, 95) / 1000.0 * 10) / 10.0);
        } else {
            result.put("avgTimeMs", 0);
            result.put("avgTimeSec", 0.0);
            result.put("p50Ms", 0);
            result.put("p95Ms", 0);
            result.put("p50Sec", 0.0);
            result.put("p95Sec", 0.0);
        }

        // --- Popular grades ---
        List<Map<String, Object>> grades = repo.countByGrade().stream()
                .map(row -> Map.of("grade", (Object) row[0], "count", row[1]))
                .collect(Collectors.toList());
        result.put("popularGrades", grades);

        // --- Popular topics ---
        List<Map<String, Object>> topics = repo.countByTopic().stream()
                .limit(10)
                .map(row -> Map.of("topic", (Object) row[0], "count", row[1]))
                .collect(Collectors.toList());
        result.put("popularTopics", topics);

        // --- Provider split ---
        List<Map<String, Object>> providers = repo.countByProvider().stream()
                .map(row -> Map.of("provider", (Object) row[0], "count", row[1]))
                .collect(Collectors.toList());
        result.put("providerSplit", providers);

        return result;
    }

    private static long percentile(List<Long> sorted, double pct) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil((pct / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
