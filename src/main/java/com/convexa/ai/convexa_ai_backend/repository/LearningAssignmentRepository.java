package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.LearningAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LearningAssignmentRepository extends JpaRepository<LearningAssignment, Long> {
    List<LearningAssignment> findByEmployeeIdOrderByDeadlineAscCreatedAtDesc(Long employeeId);
}
