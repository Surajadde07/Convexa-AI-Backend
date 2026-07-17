package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.ImprovementPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ImprovementPlanRepository extends JpaRepository<ImprovementPlan, Long> {
    List<ImprovementPlan> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
