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

    @Column(length = 500)
    private String filePath;

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