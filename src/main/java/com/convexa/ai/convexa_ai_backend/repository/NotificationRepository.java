package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdAndCompanyIdOrderByCreatedAtDesc(Long recipientId, Long companyId, Pageable pageable);

    long countByRecipientIdAndCompanyIdAndReadFalse(Long recipientId, Long companyId);

    Optional<Notification> findByIdAndRecipientIdAndCompanyId(Long id, Long recipientId, Long companyId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.id = :recipientId AND n.company.id = :companyId AND n.read = false")
    int markAllAsRead(@Param("recipientId") Long recipientId, @Param("companyId") Long companyId);
}
