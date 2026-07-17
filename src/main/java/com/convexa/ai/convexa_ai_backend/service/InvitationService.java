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

@Service
public class InvitationService {

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    public InvitationResponse createInvitation(User manager, InvitationRequest req) {

        // Cancel previous pending invitations to same email in this company
        Optional<Invitation> existingOpt = invitationRepository.findByEmailAndCompanyId(req.getEmail(), manager.getCompany().getId());
        existingOpt.ifPresent(existing -> {
            if (existing.getStatus() == InvitationStatus.PENDING) {
                existing.setStatus(InvitationStatus.CANCELLED);
                invitationRepository.save(existing);
            }
        });

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
                .expiresAt(LocalDateTime.now().plusDays(2))
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

    public List<InvitationResponse> getInvitations(Long companyId) {
        List<Invitation> list = invitationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
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

        Optional<User> existingUserOpt = userRepository.findByEmail(invite.getEmail());
        if (existingUserOpt.isPresent()) {
            User user = existingUserOpt.get();
            user.setCompany(invite.getCompany());
            user.setRole(invite.getRole());
            user.setDepartment(invite.getDepartment());
            userRepository.save(user);
        } else {
            if (req.getPassword() == null || req.getPassword().isBlank()) {
                throw new RuntimeException("Password is required for new accounts");
            }
            User user = User.builder()
                    .name(req.getName())
                    .email(invite.getEmail())
                    .password(passwordEncoder.encode(req.getPassword()))
                    .role(invite.getRole())
                    .company(invite.getCompany())
                    .department(invite.getDepartment())
                    .provider("LOCAL")
                    .build();

            userRepository.save(user);
        }

        invite.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invite);
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
