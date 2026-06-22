package com.convexa.ai.convexa_ai_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_records")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "File name is required")
    private String fileName;

    // ── Cloudinary storage fields ─────────────────────────────────────────────
    @Column(length = 1000)
    private String cloudinaryUrl;

    @Column(length = 500)
    private String cloudinaryPublicId;

    @Column(columnDefinition = "TEXT")
    @NotBlank(message = "Transcript cannot be empty")
    private String transcript;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Size(max = 50, message = "Sentiment should not exceed 50 characters")
    private String sentiment;

    @Column(length = 5000)
    private String insights;

    private Integer overallScore;

    private Integer communication;

    private Integer problemResolution;

    private Integer professionalism;

    private Integer customerSatisfaction;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    // ── NEW: timeline ──────────────────────────────────────────────────────────
    //
    // Stores the conversation timeline as a JSON string.
    // Format: [{"time":"00:00","title":"Greeting"}, ...]
    //
    // Previously the frontend fetched the timeline on-demand from
    // POST /api/calls/timeline → FastAPI /timeline.
    // That FastAPI endpoint no longer exists (merged into /analyze).
    // The Spring Boot /api/calls/timeline proxy endpoint is also removed.
    //
    // Now the controller serializes AnalyzeResponse.getTimeline() to JSON
    // using ObjectMapper and stores it here at upload time.
    // GET /api/calls/{id} returns it in the response body.
    // The frontend parses it with JSON.parse() — no extra network call needed.
    //
    // DB migration note: this adds one nullable TEXT column.
    // Existing rows will have timeline = NULL; the frontend already handles
    // null/empty timeline gracefully via its buildFallbackTimeline() function.
    // ─────────────────────────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String timeline;

    private String status;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
