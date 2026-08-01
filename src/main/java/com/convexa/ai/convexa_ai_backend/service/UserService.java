package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.AuthResponse;
import com.convexa.ai.convexa_ai_backend.dto.LoginRequest;
import com.convexa.ai.convexa_ai_backend.dto.RegisterRequest;
import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private com.convexa.ai.convexa_ai_backend.repository.OrganizationMembershipRepository organizationMembershipRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // =========================
    // REGISTER USER & WORKSPACE
    // =========================

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // 1. Create Workspace (Company)
        String companyName = request.getCompanyName();
        if (companyName == null || companyName.trim().isEmpty()) {
            companyName = request.getName() != null ? request.getName() + " Workspace" : "My Workspace";
        }

        String companySlug = generateUniqueSlug(companyName);

        Company company = Company.builder()
                .companyName(companyName)
                .companySlug(companySlug)
                .status(CompanyStatus.ACTIVE)
                .onboardingCompleted(false)
                .profileCompletionPercentage(0)
                .build();

        Company savedCompany = companyRepository.save(company);

        // 2. Create Trial Subscription
        Subscription subscription = subscriptionService.createTrialSubscription(savedCompany);
        savedCompany.setSubscription(subscription);

        // 3. Create User with role OWNER
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.OWNER)
                .company(savedCompany)
                .provider("LOCAL")
                .build();

        User savedUser = userRepository.save(user);

        // Create organization membership link (OWNER)
        OrganizationMembership membership = OrganizationMembership.builder()
                .user(savedUser)
                .company(savedCompany)
                .role(Role.OWNER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .lastActivatedAt(LocalDateTime.now())
                .createdBy(savedUser.getId())
                .build();
        organizationMembershipRepository.save(membership);

        String token = jwtService.generateToken(savedUser.getEmail(), savedUser.getId());

        return buildAuthResponse(savedUser, token, "Registration successful");
    }

    // =========================
    // LOGIN USER
    // =========================

    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!passwordMatches) {
            throw new RuntimeException("Invalid email or password");
        }

        // Model A (Company-First): authentication always succeeds so the identity is confirmed.
        // If the account has no company (removed from workspace), return noWorkspace=true.
        // The frontend redirects to /no-workspace — matching Slack/Linear behaviour where
        // the identity exists but workspace access does not.
        boolean hasActiveWorkspaces = !organizationMembershipRepository.findActiveCompaniesByUserId(user.getId()).isEmpty();
        if (!hasActiveWorkspaces) {
            String noWsToken = jwtService.generateToken(user.getEmail(), user.getId());
            return AuthResponse.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .token(noWsToken)
                    .message("No workspace associated with this account")
                    .noWorkspace(true)
                    .build();
        }

        String token = jwtService.generateToken(user.getEmail(), user.getId());

        return buildAuthResponse(user, token, "Login successful");
    }

    // =========================
    // HELPERS
    // =========================

    private String generateUniqueSlug(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            companyName = "workspace";
        }
        // Lowercase
        String base = companyName.trim().toLowerCase();
        // Replace non-alphanumeric with hyphens
        base = base.replaceAll("[^a-z0-9]+", "-");
        // Strip leading/trailing hyphens
        base = base.replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "workspace";
        }
        // Max length 50
        if (base.length() > 50) {
            base = base.substring(0, 50);
            base = base.replaceAll("-+$", "");
        }

        String candidate = base;
        int counter = 2;
        while (companyRepository.findByCompanySlug(candidate).isPresent()) {
            String suffix = "-" + counter;
            int maxBaseLen = 50 - suffix.length();
            String baseToUse = base;
            if (baseToUse.length() > maxBaseLen) {
                baseToUse = baseToUse.substring(0, maxBaseLen).replaceAll("-+$", "");
            }
            candidate = baseToUse + suffix;
            counter++;
        }
        return candidate;
    }

    public AuthResponse buildAuthResponse(User user, String token, String message) {
        return AuthResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .token(token)
                .message(message)
                .noWorkspace(user.getCompany() == null)
                .build();
    }

    @Transactional
    public void updateMemberRole(Long companyId, Long actorUserId, Long memberId, Role newRole) {
        OrganizationMembership actorMembership = organizationMembershipRepository
                .findByUserIdAndCompanyId(actorUserId, companyId)
                .orElseThrow(() -> new RuntimeException("Actor membership not found in workspace"));

        OrganizationMembership targetMembership = organizationMembershipRepository
                .findByUserIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new RuntimeException("Member does not belong to your company"));

        Role actorRole = actorMembership.getRole();
        Role targetRole = targetMembership.getRole();

        if (actorRole == Role.USER || actorRole == Role.MANAGER) {
            throw new RuntimeException("Unauthorized: USER or MANAGER cannot manage roles");
        }

        if (actorRole == Role.ADMIN) {
            if (targetRole == Role.OWNER || targetRole == Role.ADMIN) {
                throw new RuntimeException("Unauthorized: ADMIN cannot modify OWNER or ADMIN roles");
            }
            if (newRole == Role.OWNER || newRole == Role.ADMIN) {
                throw new RuntimeException("Unauthorized: ADMIN cannot promote members to OWNER or ADMIN");
            }
        }

        if (actorRole == Role.OWNER) {
            if (actorUserId.equals(memberId)) {
                throw new RuntimeException("Unauthorized: OWNER cannot modify their own role (self-demotion blocked)");
            }
            if (targetRole == Role.OWNER && newRole != Role.OWNER) {
                long ownerCount = organizationMembershipRepository.countByCompanyIdAndRoleAndStatus(companyId, Role.OWNER, MembershipStatus.ACTIVE);
                if (ownerCount <= 1) {
                    throw new RuntimeException("Cannot demote the sole OWNER of the workspace. Promote another member to OWNER first.");
                }
            }
        }

        targetMembership.setRole(newRole);
        organizationMembershipRepository.save(targetMembership);

        // Keep legacy User.role synchronized if member belongs to actor's company
        User targetUser = targetMembership.getUser();
        if (targetUser.getCompany() != null && targetUser.getCompany().getId().equals(companyId)) {
            targetUser.setRole(newRole);
            userRepository.save(targetUser);
        }
    }

    @Transactional
    public void removeMember(Long companyId, Long actorUserId, Long memberId) {
        OrganizationMembership actorMembership = organizationMembershipRepository
                .findByUserIdAndCompanyId(actorUserId, companyId)
                .orElseThrow(() -> new RuntimeException("Actor membership not found in workspace"));

        OrganizationMembership targetMembership = organizationMembershipRepository
                .findByUserIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new RuntimeException("Member does not belong to your company"));

        if (actorUserId.equals(memberId)) {
            throw new RuntimeException("Self-removal is not allowed");
        }

        Role actorRole = actorMembership.getRole();
        Role targetRole = targetMembership.getRole();

        if (actorRole == Role.USER || actorRole == Role.MANAGER) {
            throw new RuntimeException("Unauthorized to remove members");
        }

        if (actorRole == Role.ADMIN) {
            if (targetRole == Role.OWNER || targetRole == Role.ADMIN) {
                throw new RuntimeException("Unauthorized: ADMIN cannot remove OWNER or another ADMIN");
            }
        }

        if (targetRole == Role.OWNER) {
            long ownerCount = organizationMembershipRepository.countByCompanyIdAndRoleAndStatus(companyId, Role.OWNER, MembershipStatus.ACTIVE);
            if (ownerCount <= 1) {
                throw new RuntimeException("Cannot remove the sole OWNER of the workspace. Promote another member to OWNER first.");
            }
        }

        targetMembership.setStatus(MembershipStatus.LEFT);
        targetMembership.setRemovedBy(actorUserId);
        targetMembership.setRemovedAt(LocalDateTime.now());
        organizationMembershipRepository.save(targetMembership);

        // Clear legacy users.company_id ONLY if target user's current company_id matches this workspace
        User targetUser = targetMembership.getUser();
        if (targetUser.getCompany() != null && targetUser.getCompany().getId().equals(companyId)) {
            targetUser.setCompany(null);
            userRepository.save(targetUser);
        }

        subscriptionService.decrementSeatCount(companyId);
    }

    public com.convexa.ai.convexa_ai_backend.dto.PagedMembersResponse getMembers(
            Long companyId, int page, int size, String search, String role, String sort) {
        
        List<OrganizationMembership> memberships = organizationMembershipRepository.findByCompanyIdAndStatus(companyId, MembershipStatus.ACTIVE);

        java.util.stream.Stream<OrganizationMembership> stream = memberships.stream();
        if (search != null && !search.trim().isEmpty()) {
            String q = search.trim().toLowerCase();
            stream = stream.filter(m -> {
                User u = m.getUser();
                return (u.getName() != null && u.getName().toLowerCase().contains(q)) || 
                       (u.getEmail() != null && u.getEmail().toLowerCase().contains(q));
            });
        }

        if (role != null && !role.trim().isEmpty() && !"ALL".equalsIgnoreCase(role)) {
            stream = stream.filter(m -> m.getRole() != null && m.getRole().name().equalsIgnoreCase(role));
        }

        List<OrganizationMembership> filtered = stream.collect(java.util.stream.Collectors.toList());

        if (sort != null && !sort.trim().isEmpty()) {
            String[] parts = sort.split(",");
            String field = parts[0];
            boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
            filtered.sort((m1, m2) -> {
                User u1 = m1.getUser();
                User u2 = m2.getUser();
                int comp = 0;
                if ("name".equalsIgnoreCase(field)) {
                    String n1 = u1.getName() != null ? u1.getName() : "";
                    String n2 = u2.getName() != null ? u2.getName() : "";
                    comp = n1.compareToIgnoreCase(n2);
                } else if ("email".equalsIgnoreCase(field)) {
                    comp = u1.getEmail().compareToIgnoreCase(u2.getEmail());
                } else {
                    LocalDateTime t1 = m1.getJoinedAt() != null ? m1.getJoinedAt() : LocalDateTime.MIN;
                    LocalDateTime t2 = m2.getJoinedAt() != null ? m2.getJoinedAt() : LocalDateTime.MIN;
                    comp = t1.compareTo(t2);
                }
                return desc ? -comp : comp;
            });
        }

        int totalElements = filtered.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int start = page * size;
        List<com.convexa.ai.convexa_ai_backend.dto.EmployeeResponse> content = new java.util.ArrayList<>();
        if (start < totalElements) {
            int end = Math.min(start + size, totalElements);
            for (int i = start; i < end; i++) {
                OrganizationMembership m = filtered.get(i);
                User u = m.getUser();
                content.add(com.convexa.ai.convexa_ai_backend.dto.EmployeeResponse.builder()
                    .id(u.getId())
                    .name(u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail())
                    .email(u.getEmail())
                    .role(m.getRole() != null ? m.getRole().name() : "USER")
                    .department(m.getDepartment() != null ? m.getDepartment() : u.getDepartment())
                    .createdAt(m.getJoinedAt() != null ? m.getJoinedAt() : u.getCreatedAt())
                    .build());
            }
        }

        return com.convexa.ai.convexa_ai_backend.dto.PagedMembersResponse.builder()
            .content(content)
            .page(page)
            .size(size)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .build();
    }
}