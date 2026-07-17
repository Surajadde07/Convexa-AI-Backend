package com.convexa.ai.convexa_ai_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "learning_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @Column(length = 100, nullable = false)
    private String moduleName; // Communication, Closing, Negotiation, Professionalism, Problem Resolution

    private LocalDate deadline;
    
    @Column(length = 50)
    private String priority;
    
    @Column(length = 50)
    private String status; // Assigned, Completed

    private LocalDate assignedDate;
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.assignedDate == null) {
            this.assignedDate = LocalDate.now();
        }
        if (this.status == null) {
            this.status = "Assigned";
        }
    }
}
