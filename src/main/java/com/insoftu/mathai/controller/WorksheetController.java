package com.insoftu.mathai.controller;

import com.insoftu.mathai.model.WorksheetRequest;
import com.insoftu.mathai.model.WorksheetResponse;
import com.insoftu.mathai.service.GeminiService;
import com.insoftu.mathai.service.PdfService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WorksheetController {

    private final GeminiService geminiService;
    private final PdfService pdfService;

    public WorksheetController(GeminiService geminiService, PdfService pdfService) {
        this.geminiService = geminiService;
        this.pdfService = pdfService;
    }

    /**
     * Returns the list of IB-aligned topics for a given grade.
     */
    @GetMapping("/topics")
    public ResponseEntity<Map<String, List<String>>> getTopics(@RequestParam int grade) {
        return ResponseEntity.ok(Map.of("topics", topicsForGrade(grade)));
    }

    /**
     * Generates a worksheet as JSON (for browser rendering + browser print).
     */
    @PostMapping("/worksheet/generate")
    public ResponseEntity<WorksheetResponse> generate(@Valid @RequestBody WorksheetRequest request) {
        WorksheetResponse worksheet = geminiService.generateWorksheet(request);
        return ResponseEntity.ok(worksheet);
    }

    /**
     * Generates a worksheet and returns it directly as a downloadable PDF.
     */
    @PostMapping("/worksheet/pdf")
    public ResponseEntity<byte[]> generatePdf(@Valid @RequestBody WorksheetRequest request) {
        WorksheetResponse worksheet = geminiService.generateWorksheet(request);
        byte[] pdf = pdfService.generatePdf(worksheet);

        String filename = ("IB_Math_Grade" + request.grade() + "_" + request.topic())
                .replaceAll("[^a-zA-Z0-9_]", "_") + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── IB Curriculum Topics by Grade ─────────────────────────────────────────

    private List<String> topicsForGrade(int grade) {
        return switch (grade) {
            case 6 -> List.of(
                    "Number Systems & Place Value",
                    "Fractions, Decimals & Percentages",
                    "Basic Algebra & Expressions",
                    "Ratios & Proportions",
                    "Geometry – Area & Perimeter",
                    "Introduction to Statistics",
                    "Integers & Number Lines"
            );
            case 7 -> List.of(
                    "Integers & Rational Numbers",
                    "Algebraic Expressions & Equations",
                    "Ratios, Rates & Percentages",
                    "Geometry – Angles & Triangles",
                    "Area & Volume",
                    "Probability Basics",
                    "Coordinate Geometry"
            );
            case 8 -> List.of(
                    "Linear Equations & Inequalities",
                    "Systems of Equations",
                    "Powers & Exponents",
                    "Pythagoras Theorem",
                    "Geometry – Circles & Polygons",
                    "Introduction to Functions",
                    "Data & Statistics"
            );
            case 9 -> List.of(
                    "Quadratic Equations",
                    "Simultaneous Equations",
                    "Trigonometry – SOH CAH TOA",
                    "Surds & Indices",
                    "Linear & Quadratic Functions",
                    "Mensuration – 3D Shapes",
                    "Probability – Combined Events"
            );
            case 10 -> List.of(
                    "Quadratic Functions & Graphs",
                    "Trigonometry – Sine & Cosine Rules",
                    "Sequences & Series",
                    "Circle Theorems",
                    "Vectors in 2D",
                    "Statistics – Mean, Median, Mode, IQR",
                    "Exponential Functions"
            );
            case 11 -> List.of(
                    "DP Algebra & Functions",
                    "Trigonometry – Unit Circle & Identities",
                    "Exponential & Logarithmic Functions",
                    "Sequences & Series (Arithmetic & Geometric)",
                    "Differentiation – Basics",
                    "Statistics & Probability (DP)",
                    "Vectors in 3D"
            );
            case 12 -> List.of(
                    "Calculus – Differentiation",
                    "Calculus – Integration",
                    "Probability Distributions",
                    "Matrices",
                    "Complex Numbers",
                    "Further Trigonometry",
                    "Mathematical Induction"
            );
            default -> List.of("General Mathematics");
        };
    }
}

