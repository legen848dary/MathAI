package com.insoftu.mathai.analytics.repository;

import com.insoftu.mathai.analytics.model.WorksheetGeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorksheetGenerationRepository extends JpaRepository<WorksheetGeneration, UUID> {

    long countByCreatedAtAfter(Instant after);

    long countBySuccessTrueAndCreatedAtAfter(Instant after);

    long countByCreatedAtBetween(Instant start, Instant end);

    @Query("SELECT AVG(w.elapsedMs) FROM WorksheetGeneration w WHERE w.success = true AND w.createdAt > :after")
    Double avgElapsedMsSince(@Param("after") Instant after);

    @Query(value = """
        SELECT elapsed_ms FROM worksheet_generations
        WHERE success = true AND created_at > :after
        ORDER BY elapsed_ms
        """, nativeQuery = true)
    List<Long> findAllElapsedMsSince(@Param("after") Instant after);

    @Query("SELECT w.grade, COUNT(w) FROM WorksheetGeneration w WHERE w.success = true GROUP BY w.grade ORDER BY COUNT(w) DESC")
    List<Object[]> countByGrade();

    @Query("SELECT w.topic, COUNT(w) FROM WorksheetGeneration w WHERE w.success = true GROUP BY w.topic ORDER BY COUNT(w) DESC")
    List<Object[]> countByTopic();

    @Query("SELECT w.provider, COUNT(w) FROM WorksheetGeneration w WHERE w.success = true GROUP BY w.provider ORDER BY COUNT(w) DESC")
    List<Object[]> countByProvider();
}
