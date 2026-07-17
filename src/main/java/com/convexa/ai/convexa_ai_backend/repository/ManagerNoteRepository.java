package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.ManagerNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ManagerNoteRepository extends JpaRepository<ManagerNote, Long> {
    List<ManagerNote> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
