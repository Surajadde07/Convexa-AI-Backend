package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    List<Invitation> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    Optional<Invitation> findByToken(String token);
    Optional<Invitation> findByEmailAndCompanyId(String email, Long companyId);
}
