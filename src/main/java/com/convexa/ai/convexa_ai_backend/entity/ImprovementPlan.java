package com.convexa.ai.convexa_ai_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "improvement_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImprovementPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    private Integer targetQA;
    
    @Column(length = 50)
    private String targetSentiment;
    
    private LocalDate deadline;
    
    @Column(columnDefinition = "TEXT")
    private String assignedModules; // Comma separated list of module names
    
    private Integer progress; // e.g. 0 to 100
    
    @Column(length = 50)
    private String status; // Active, Completed, Overdue

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.progress == null) {
            this.progress = 0;
        }
        if (this.status == null) {
            this.status = "Active";
        }
    }
}
