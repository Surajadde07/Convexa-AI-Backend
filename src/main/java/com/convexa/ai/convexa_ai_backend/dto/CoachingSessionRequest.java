package com.convexa.ai.convexa_ai_backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class CoachingSessionRequest {
    private LocalDate sessionDate;
    private String sessionTime;
    private String reason;
    private String priority;
    private String notes;
    private String status; // Optional: Pending, Completed, Cancelled
}
