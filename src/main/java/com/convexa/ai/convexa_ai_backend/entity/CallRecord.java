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

    // ── timeline ───────────────────────────────────────────────────────────────
    // Stores the conversation timeline as a JSON string.
    // Format: [{"time":"00:00","title":"Greeting"}, ...]
    // Serialized from AnalyzeResponse.getTimeline() (List<Map<String,String>>)
    // by the controller using ObjectMapper at upload time.
    // Existing rows: NULL — frontend handles gracefully via buildFallbackTimeline().
    // ─────────────────────────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String timeline;

    // ── Sprint 1 new fields ────────────────────────────────────────────────────
    //
    // All nine new fields follow the same storage convention already used by
    // timeline, strengths, and improvements: complex structures (arrays,
    // objects) are serialized to JSON strings by the controller using
    // ObjectMapper before being saved, and deserialized by the frontend with
    // JSON.parse().  Scalar strings (outcomeStatus, callType, buyingIntent)
    // are stored directly.
    //
    // Every column is nullable TEXT so that existing rows without these values
    // load without error — Hibernate simply returns null for missing columns
    // and the controller/frontend apply safe defaults at read time.
    //
    // ── outcomeStatus ──
    // Scalar string. One of: Won | Lost | Follow Up Required | Escalated | Pending
    // Stored as a plain VARCHAR-compatible string (no columnDefinition needed
    // since it never exceeds 50 chars, but TEXT keeps it consistent).
    @Column(columnDefinition = "TEXT")
    private String outcomeStatus;

    // ── actionItems ──
    // JSON array of objects: [{"title":"...","completed":false}, ...]
    // Serialized from AnalyzeResponse.getActionItems() (List<Map<String,Object>>).
    // "completed" is a boolean in the JSON; we use Map<String,Object> in the DTO
    // so Jackson preserves the boolean type rather than coercing to String.
    @Column(columnDefinition = "TEXT")
    private String actionItems;

    // ── riskFlags ──
    // JSON array of objects: [{"severity":"High","message":"..."}, ...]
    // Serialized from AnalyzeResponse.getRiskFlags() (List<Map<String,String>>).
    @Column(columnDefinition = "TEXT")
    private String riskFlags;

    // ── followUpSuggestions ──
    // JSON array of strings: ["Suggestion 1", "Suggestion 2", ...]
    // Serialized from AnalyzeResponse.getFollowUpSuggestions() (List<String>).
    @Column(columnDefinition = "TEXT")
    private String followUpSuggestions;

    // ── confidence ──
    // Integer 0-100. AI self-reported confidence in the accuracy of its output.
    // Stored as Integer (nullable) — null means the field was absent (old record).
    private Integer confidence;

    // ── callType ──
    // Scalar string. One of: Inbound Support | Outbound Sales | Renewal |
    // Onboarding | Escalation | Collections | Follow-Up | Discovery | Demo | Negotiation
    @Column(columnDefinition = "TEXT")
    private String callType;

    // ── buyingIntent ──
    // Scalar string. One of: High | Medium | Low | None | N/A
    @Column(columnDefinition = "TEXT")
    private String buyingIntent;

    // ── buyingSignals ──
    // JSON array of strings: ["Signal 1", "Signal 2", ...]
    // Serialized from AnalyzeResponse.getBuyingSignals() (List<String>).
    @Column(columnDefinition = "TEXT")
    private String buyingSignals;

    // ── objections ──
    // JSON array of objects: [{"objection":"...","resolved":true}, ...]
    // Serialized from AnalyzeResponse.getObjections() (List<Map<String,Object>>).
    // "resolved" is a boolean; Map<String,Object> preserves the type.
    @Column(columnDefinition = "TEXT")
    private String objections;

    // ─────────────────────────────────────────────────────────────────────────

    private String status;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}