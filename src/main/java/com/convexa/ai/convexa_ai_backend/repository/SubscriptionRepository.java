package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByCompanyId(Long companyId);
}
