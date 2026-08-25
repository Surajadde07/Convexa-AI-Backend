package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.AcceptInvitationRequest;
import com.convexa.ai.convexa_ai_backend.dto.InvitationRequest;
import com.convexa.ai.convexa_ai_backend.dto.InvitationResponse;
import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.InvitationRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.convexa.ai.convexa_ai_backend.exception.DuplicatePendingInvitationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class InvitationService {

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.convexa.ai.convexa_ai_backend.repository.OrganizationMembershipRepository organizationMembershipRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private NotificationService notificationService;

    public InvitationResponse createInvitation(User manager, InvitationRequest req) {

        // Check if user already belongs to this company (active member check)
        Optional<User> existingUser = userRepository.findByEmail(req.getEmail());
        if (existingUser.isPresent()) {
            User existingMember = existingUser.get();
            if (existingMember.getCompany() != null && existingMember.getCompany().getId().equals(manager.getCompany().getId())) {
                throw new DuplicatePendingInvitationException("This user is already an active member of your workspace.");
            }
        }

        // Proactive seat limit check — must happen BEFORE creating the invitation record
        // or sending any email. If the workspace is full, only the OWNER/ADMIN sees this
        // error. The employee should never receive an invitation they cannot accept.
        subscriptionService.checkSeatAvailability(manager.getCompany().getId());
        Optional<Invitation> existingOpt = invitationRepository.findByEmailAndCompanyId(req.getEmail(), manager.getCompany().getId());
        if (existingOpt.isPresent()) {
            Invitation existing = existingOpt.get();
            if (existing.getStatus() == InvitationStatus.PENDING && existing.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw new DuplicatePendingInvitationException("Invitation already pending for this email. Use resend to refresh the invitation link.");
            }
            if (existing.getStatus() == InvitationStatus.PENDING) {
                existing.setStatus(InvitationStatus.CANCELLED);
                invitationRepository.save(existing);
            }
        }

        Role targetRole;
        try {
            targetRole = Role.valueOf(req.getRole().toUpperCase());
        } catch (Exception e) {
            targetRole = Role.USER;
        }

        String token = UUID.randomUUID().toString();
        Invitation invite = Invitation.builder()
                .company(manager.getCompany())
                .email(req.getEmail())
                .role(targetRole)
                .department(req.getDepartment())
                .invitedBy(manager)
                .token(token)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        invitationRepository.save(invite);
        emailService.sendInvitationEmail(
                invite.getEmail(),
                manager.getCompany().getCompanyName(),
                manager.getCompany().getCompanyLogo(),
                invite.getRole().name(),
                invite.getDepartment(),
                manager.getName() != null && !manager.getName().isBlank() ? manager.getName() : manager.getEmail(),
                invite.getToken(),
                invite.getExpiresAt().toString()
        );

        return mapToResponse(invite);
    }

    public List<InvitationResponse> getInvitations(User actor) {
        Long companyId = actor.getCompany().getId();
        List<Invitation> list;

        // OWNER sees all company invitations for full workspace oversight.
        // ADMIN and MANAGER see only invitations they personally created.
        if (actor.getRole() == Role.OWNER) {
            list = invitationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        } else {
            list = invitationRepository.findByCompanyIdAndInvitedByIdOrderByCreatedAtDesc(companyId, actor.getId());
        }

        LocalDateTime now = LocalDateTime.now();
        for (Invitation invite : list) {
            if (invite.getStatus() == InvitationStatus.PENDING && invite.getExpiresAt().isBefore(now)) {
                invite.setStatus(InvitationStatus.EXPIRED);
                invitationRepository.save(invite);
            }
        }

        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public void cancelInvitation(User manager, Long invitationId) {
        Invitation invite = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));

        if (!invite.getCompany().getId().equals(manager.getCompany().getId())) {
            throw new RuntimeException("Unauthorized: Invitation belongs to a different company");
        }

        if (invite.getStatus() != InvitationStatus.PENDING) {
            throw new RuntimeException("Only pending invitations can be cancelled");
        }

        invite.setStatus(InvitationStatus.CANCELLED);
        invitationRepository.save(invite);
    }

    public InvitationResponse getInvitationByToken(String token) {
        Invitation invite = invitationRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid invitation token"));

        if (invite.getStatus() == InvitationStatus.PENDING && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invite);
        }

        return mapToResponse(invite);
    }

    @Transactional
    public void acceptInvitation(AcceptInvitationRequest req) {
        Invitation invite = invitationRepository.findByToken(req.getToken())
                .orElseThrow(() -> new RuntimeException("Invalid invitation token"));

        if (invite.getStatus() == InvitationStatus.PENDING && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invite);
        }

        if (invite.getStatus() != InvitationStatus.PENDING) {
            throw new RuntimeException("Invitation is no longer active (Status: " + invite.getStatus() + ")");
        }

        User user;
        Optional<User> existingUserOpt = userRepository.findByEmail(invite.getEmail());
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
        } else {
            if (req.getPassword() == null || req.getPassword().isBlank()) {
                throw new RuntimeException("Password is required for new accounts");
            }
            user = User.builder()
                    .name(req.getName())
                    .email(invite.getEmail())
                    .password(passwordEncoder.encode(req.getPassword()))
                    .role(invite.getRole())
                    .company(invite.getCompany())
                    .department(invite.getDepartment())
                    .provider("LOCAL")
                    .build();

            user = userRepository.save(user);
        }

        // Duplicate Membership Protection Check
        Optional<OrganizationMembership> existingMembershipOpt = 
                organizationMembershipRepository.findByUserIdAndCompanyId(user.getId(), invite.getCompany().getId());

        if (existingMembershipOpt.isPresent()) {
            OrganizationMembership membership = existingMembershipOpt.get();
            if (membership.getStatus() == MembershipStatus.ACTIVE) {
                throw new RuntimeException("User is already an active member of this workspace.");
            } else if (membership.getStatus() == MembershipStatus.PENDING) {
                throw new RuntimeException("Invitation has already been accepted or is processing.");
            } else if (membership.getStatus() == MembershipStatus.REMOVED || membership.getStatus() == MembershipStatus.LEFT) {
                // Restoration flow
                membership.setStatus(MembershipStatus.ACTIVE);
                membership.setLastActivatedAt(LocalDateTime.now());
                membership.setRemovedBy(null);
                membership.setRemovedAt(null);
                membership.setRole(invite.getRole());
                membership.setDepartment(invite.getDepartment());
                organizationMembershipRepository.save(membership);
            }
        } else {
            // New membership
            OrganizationMembership newMembership = OrganizationMembership.builder()
                    .user(user)
                    .company(invite.getCompany())
                    .role(invite.getRole())
                    .department(invite.getDepartment())
                    .status(MembershipStatus.ACTIVE)
                    .joinedAt(LocalDateTime.now())
                    .lastActivatedAt(LocalDateTime.now())
                    .build();
            organizationMembershipRepository.save(newMembership);
        }

        // Legacy dual-write update on User entity
        user.setCompany(invite.getCompany());
        user.setRole(invite.getRole());
        user.setDepartment(invite.getDepartment());
        userRepository.save(user);

        invite.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invite);

        // Synchronize active subscription seat counts
        subscriptionService.incrementSeatCount(invite.getCompany().getId());

        // Notify inviter/owner
        try {
            notificationService.createMemberJoinedNotification(invite.getCompany(), invite.getInvitedBy(), user);
        } catch (Exception notifEx) {
            log.warn("Failed to create member joined notification: {}", notifEx.getMessage());
        }
    }

    @Transactional
    public InvitationResponse resendInvitation(User actor, Long invitationId) {
        Invitation invite = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));

        if (!invite.getCompany().getId().equals(actor.getCompany().getId())) {
            throw new RuntimeException("Unauthorized: Invitation belongs to a different company");
        }

        if (invite.getStatus() != InvitationStatus.PENDING && invite.getStatus() != InvitationStatus.EXPIRED) {
            throw new RuntimeException("Only pending or expired invitations can be re-sent");
        }

        String newToken = UUID.randomUUID().toString();
        invite.setToken(newToken);
        invite.setStatus(InvitationStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        invite.setCreatedAt(LocalDateTime.now());
        invitationRepository.save(invite);

        emailService.sendInvitationEmail(
                invite.getEmail(),
                actor.getCompany().getCompanyName(),
                actor.getCompany().getCompanyLogo(),
                invite.getRole().name(),
                invite.getDepartment(),
                actor.getName() != null && !actor.getName().isBlank() ? actor.getName() : actor.getEmail(),
                invite.getToken(),
                invite.getExpiresAt().toString()
        );

        return mapToResponse(invite);
    }

    private InvitationResponse mapToResponse(Invitation invite) {
        return InvitationResponse.builder()
                .id(invite.getId())
                .email(invite.getEmail())
                .role(invite.getRole().name())
                .department(invite.getDepartment())
                .invitedBy(invite.getInvitedBy() != null ? (invite.getInvitedBy().getName() != null ? invite.getInvitedBy().getName() : invite.getInvitedBy().getEmail()) : "System")
                .token(invite.getToken())
                .status(invite.getStatus())
                .expiresAt(invite.getExpiresAt())
                .createdAt(invite.getCreatedAt())
                .userExists(userRepository.existsByEmail(invite.getEmail()))
                .build();
    }
}
