package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.NotificationDTO;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.Notification;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.CallRecordRepository;
import com.convexa.ai.convexa_ai_backend.repository.NotificationRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CallRecordRepository callRecordRepository;
    private final UserRepository userRepository;

    /**
     * Retrieve recent notifications for the authenticated user, scoped strictly by company and recipient.
     */
    @Transactional
    public List<NotificationDTO> getNotifications(WorkspacePrincipal principal, int limit) {
        if (principal == null || principal.getUserId() == null || principal.getCompanyId() == null) {
            return List.of();
        }

        seedInitialNotificationsIfEmpty(principal);

        int max = Math.min(Math.max(limit, 1), 50);
        List<Notification> list = notificationRepository.findByRecipientIdAndCompanyIdOrderByCreatedAtDesc(
                principal.getUserId(),
                principal.getCompanyId(),
                PageRequest.of(0, max)
        );

        return list.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /**
     * Get the unread notification count for the authenticated user and company.
     */
    public long getUnreadCount(WorkspacePrincipal principal) {
        if (principal == null || principal.getUserId() == null || principal.getCompanyId() == null) {
            return 0;
        }
        return notificationRepository.countByRecipientIdAndCompanyIdAndReadFalse(
                principal.getUserId(),
                principal.getCompanyId()
        );
    }

    /**
     * Mark a single notification as read.
     */
    @Transactional
    public void markAsRead(WorkspacePrincipal principal, Long id) {
        if (principal == null || id == null) return;
        notificationRepository.findByIdAndRecipientIdAndCompanyId(id, principal.getUserId(), principal.getCompanyId())
                .ifPresent(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }

    /**
     * Mark all unread notifications for the user as read.
     */
    @Transactional
    public void markAllAsRead(WorkspacePrincipal principal) {
        if (principal == null) return;
        notificationRepository.markAllAsRead(principal.getUserId(), principal.getCompanyId());
    }

    /**
     * Core helper to create and persist a notification.
     */
    @Transactional
    public Notification createNotification(Company company, User recipient, String type, String title, String message, String referenceType, Long referenceId) {
        if (company == null || recipient == null) {
            log.warn("Cannot create notification: company or recipient is null");
            return null;
        }

        Notification notification = Notification.builder()
                .company(company)
                .recipient(recipient)
                .type(type)
                .title(title)
                .message(message)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        return notificationRepository.save(notification);
    }

    /**
     * Generates a notification when a call recording finishes transcription & analysis.
     */
    @Transactional
    public void createCallAnalysisCompletedNotification(Company company, User uploader, CallRecord call) {
        if (company == null || uploader == null || call == null) return;

        createNotification(
                company,
                uploader,
                "CALL_ANALYSIS_COMPLETED",
                "Call Analysis Completed",
                (call.getFileName() != null ? call.getFileName() : "Call recording") + " has finished AI transcription and scorecard evaluation.",
                "CALL",
                call.getId()
        );
    }

    /**
     * Generates a high-risk / coaching notification if call score is low or risk flags are detected.
     */
    @Transactional
    public void createHighRiskDetectedNotification(Company company, User uploader, CallRecord call, String riskDetail) {
        if (company == null || uploader == null || call == null) return;

        String msg = (call.getFileName() != null ? call.getFileName() : "A conversation") + " was flagged for coaching review (QA Score: " + (call.getOverallScore() != null ? call.getOverallScore() : "N/A") + "/100).";
        if (riskDetail != null && !riskDetail.isBlank()) {
            msg += " " + riskDetail;
        }

        createNotification(
                company,
                uploader,
                "HIGH_RISK_DETECTED",
                "High-Risk Conversation Detected",
                msg,
                "CALL",
                call.getId()
        );
    }

    /**
     * Generates a notification when call processing encounters an error.
     */
    @Transactional
    public void createCallAnalysisFailedNotification(Company company, User uploader, String fileName, String errorMsg) {
        if (company == null || uploader == null) return;

        createNotification(
                company,
                uploader,
                "CALL_ANALYSIS_FAILED",
                "Call Analysis Failed",
                (fileName != null ? fileName : "Call recording") + " could not be analyzed: " + (errorMsg != null ? errorMsg : "Processing error."),
                "SYSTEM",
                null
        );
    }

    /**
     * Generates a notification when an invited member joins the workspace.
     */
    @Transactional
    public void createMemberJoinedNotification(Company company, User invitedBy, User newMember) {
        if (company == null || newMember == null) return;

        User recipient = invitedBy;
        if (recipient == null) {
            // Fallback to finding an owner user in company
            List<User> owners = userRepository.findByCompanyIdAndRole(company.getId(), com.convexa.ai.convexa_ai_backend.entity.Role.OWNER);
            if (!owners.isEmpty()) recipient = owners.get(0);
        }

        if (recipient != null) {
            String name = newMember.getName() != null && !newMember.getName().isBlank() ? newMember.getName() : newMember.getEmail();
            createNotification(
                    company,
                    recipient,
                    "MEMBER_JOINED",
                    "New Member Joined Workspace",
                    name + " accepted the invitation and joined " + company.getCompanyName() + ".",
                    "MEMBER",
                    newMember.getId()
            );
        }
    }

    /**
     * Seed initial real notifications from actual company calls if table has no notifications for this user.
     * Prevents empty initial state for existing accounts.
     */
    @Transactional
    public void seedInitialNotificationsIfEmpty(WorkspacePrincipal principal) {
        long count = notificationRepository.countByRecipientIdAndCompanyIdAndReadFalse(principal.getUserId(), principal.getCompanyId());
        if (count > 0) return;

        List<Notification> existing = notificationRepository.findByRecipientIdAndCompanyIdOrderByCreatedAtDesc(
                principal.getUserId(),
                principal.getCompanyId(),
                PageRequest.of(0, 1)
        );
        if (!existing.isEmpty()) return;

        // Populate from real historical calls
        User user = userRepository.findById(principal.getUserId()).orElse(null);
        if (user == null || user.getCompany() == null) return;

        List<CallRecord> calls = callRecordRepository.findByCompanyIdOrderByCreatedAtDesc(principal.getCompanyId());
        if (calls == null || calls.isEmpty()) return;

        List<Notification> initial = new ArrayList<>();
        // Take up to 4 most recent calls to create realistic, genuine notifications
        for (int i = 0; i < Math.min(4, calls.size()); i++) {
            CallRecord c = calls.get(i);
            if (c.getOverallScore() != null && c.getOverallScore() < 50) {
                initial.add(Notification.builder()
                        .company(user.getCompany())
                        .recipient(user)
                        .type("HIGH_RISK_DETECTED")
                        .title("High-Risk Conversation Detected")
                        .message((c.getFileName() != null ? c.getFileName() : "Call") + " scored " + c.getOverallScore() + "/100 — review recommended.")
                        .referenceType("CALL")
                        .referenceId(c.getId())
                        .read(false)
                        .createdAt(c.getCreatedAt() != null ? c.getCreatedAt() : LocalDateTime.now().minusHours(i + 1))
                        .build());
            } else {
                initial.add(Notification.builder()
                        .company(user.getCompany())
                        .recipient(user)
                        .type("CALL_ANALYSIS_COMPLETED")
                        .title("Call Analysis Completed")
                        .message((c.getFileName() != null ? c.getFileName() : "Call") + " finished processing and scorecard grading.")
                        .referenceType("CALL")
                        .referenceId(c.getId())
                        .read(i > 1) // Mark older ones as read
                        .createdAt(c.getCreatedAt() != null ? c.getCreatedAt() : LocalDateTime.now().minusHours(i + 1))
                        .build());
            }
        }

        if (!initial.isEmpty()) {
            notificationRepository.saveAll(initial);
        }
    }

    private NotificationDTO mapToDTO(Notification n) {
        return NotificationDTO.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt() != null ? n.getCreatedAt().toString() : null)
                .timeAgo(formatTimeAgo(n.getCreatedAt()))
                .build();
    }

    private String formatTimeAgo(LocalDateTime dt) {
        if (dt == null) return "just now";
        Duration d = Duration.between(dt, LocalDateTime.now());
        long mins = d.toMinutes();
        if (mins < 1) return "just now";
        if (mins < 60) return mins + "m ago";
        long hrs = d.toHours();
        if (hrs < 24) return hrs + "h ago";
        long days = d.toDays();
        return days + "d ago";
    }
}
