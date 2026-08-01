package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {

    List<CallRecord> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<CallRecord> findByIdAndCompanyId(Long id, Long companyId);

    List<CallRecord> findByUserIdAndCompanyIdOrderByCreatedAtDesc(Long userId, Long companyId);

    List<CallRecord> findByUserId(Long userId);
}