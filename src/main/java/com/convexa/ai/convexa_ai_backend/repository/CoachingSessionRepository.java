package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.CoachingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CoachingSessionRepository extends JpaRepository<CoachingSession, Long> {
    List<CoachingSession> findByEmployeeIdOrderBySessionDateDescCreatedAtDesc(Long employeeId);
}
