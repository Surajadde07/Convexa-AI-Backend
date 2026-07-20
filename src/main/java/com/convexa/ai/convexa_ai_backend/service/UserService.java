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
                .status("ACTIVE")
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

        String token = jwtService.generateToken(savedUser.getEmail());

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
        if (user.getCompany() == null) {
            String noWsToken = jwtService.generateToken(user.getEmail());
            return AuthResponse.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .role(user.getRole() != null ? user.getRole().name() : "USER")
                    .token(noWsToken)
                    .message("No workspace associated with this account")
                    .noWorkspace(true)
                    .build();
        }

        String token = jwtService.generateToken(user.getEmail());

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
        String companyName = null;
        String companySlug = null;
        String companyLogo = null;
        String managerName = "System Manager";

        String subscriptionPlan = null;
        String subscriptionStatus = null;
        Integer seatLimit = null;
        Integer currentSeatCount = null;
        String trialEndsAt = null;

        Boolean onboardingCompleted = null;
        Integer profileCompletionPercentage = null;

        String brandPrimaryColor = null;
        String brandSecondaryColor = null;

        if (user.getCompany() != null) {
            Company company = user.getCompany();
            companyName = company.getCompanyName();
            companySlug = company.getCompanySlug();
            companyLogo = company.getCompanyLogo();
            onboardingCompleted = company.getOnboardingCompleted();
            profileCompletionPercentage = company.getProfileCompletionPercentage();
            brandPrimaryColor = company.getBrandPrimaryColor();
            brandSecondaryColor = company.getBrandSecondaryColor();

            if (companyLogo == null || companyLogo.isBlank()) {
                companyLogo = "https://via.placeholder.com/150?text=Convexa+AI";
            }

            Subscription sub = company.getSubscription();
            if (sub != null) {
                subscriptionPlan = sub.getPlan().name();
                subscriptionStatus = sub.getStatus().name();
                seatLimit = sub.getSeatLimit();
                currentSeatCount = sub.getCurrentSeatCount();
                if (sub.getTrialEnd() != null) {
                    trialEndsAt = sub.getTrialEnd().toString();
                }
            }

            List<User> companyUsers = userRepository.findByCompanyId(company.getId());
            for (User cu : companyUsers) {
                if (cu.getRole() == Role.OWNER || cu.getRole() == Role.MANAGER || cu.getRole() == Role.ADMIN) {
                    managerName = cu.getName() != null && !cu.getName().isBlank() ? cu.getName() : cu.getEmail();
                    break;
                }
            }
        }

        return AuthResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .token(token)
                .message(message)
                .companyName(companyName)
                .companySlug(companySlug)
                .companyLogo(companyLogo)
                .department(user.getDepartment())
                .managerName(managerName)
                .subscriptionPlan(subscriptionPlan)
                .subscriptionStatus(subscriptionStatus)
                .seatLimit(seatLimit)
                .currentSeatCount(currentSeatCount)
                .trialEndsAt(trialEndsAt)
                .onboardingCompleted(onboardingCompleted)
                .profileCompletionPercentage(profileCompletionPercentage)
                .brandPrimaryColor(brandPrimaryColor)
                .brandSecondaryColor(brandSecondaryColor)
                .build();
    }

    @Transactional
    public void updateMemberRole(User actor, Long memberId, Role newRole) {
        User target = userRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (target.getCompany() == null || !target.getCompany().getId().equals(actor.getCompany().getId())) {
            throw new RuntimeException("Member does not belong to your company");
        }

        Role actorRole = actor.getRole();
        Role targetRole = target.getRole();

        if (actorRole == Role.USER) {
            throw new RuntimeException("Unauthorized: USER cannot manage roles");
        }
        if (actorRole == Role.MANAGER) {
            throw new RuntimeException("Unauthorized: MANAGER cannot manage roles");
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
            if (target.getId().equals(actor.getId())) {
                throw new RuntimeException("Unauthorized: OWNER cannot modify their own role (self-demotion blocked)");
            }
        }

        target.setRole(newRole);
        userRepository.save(target);
    }

    @Transactional
    public void removeMember(User actor, Long memberId) {
        User target = userRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (target.getCompany() == null || !target.getCompany().getId().equals(actor.getCompany().getId())) {
            throw new RuntimeException("Member does not belong to your company");
        }

        if (target.getRole() == Role.OWNER) {
            throw new RuntimeException("Cannot remove an OWNER from the workspace");
        }

        if (target.getId().equals(actor.getId())) {
            throw new RuntimeException("Self-removal is not allowed");
        }

        Role actorRole = actor.getRole();
        Role targetRole = target.getRole();

        if (actorRole == Role.USER || actorRole == Role.MANAGER) {
            throw new RuntimeException("Unauthorized to remove members");
        }
        if (actorRole == Role.ADMIN) {
            if (targetRole == Role.OWNER || targetRole == Role.ADMIN) {
                throw new RuntimeException("Unauthorized: ADMIN cannot remove OWNER or another ADMIN");
            }
        }

        Long companyId = target.getCompany().getId();
        target.setCompany(null);
        userRepository.save(target);

        subscriptionService.decrementSeatCount(companyId);
    }

    public com.convexa.ai.convexa_ai_backend.dto.PagedMembersResponse getMembers(
            Long companyId, int page, int size, String search, String role, String sort) {
        
        List<User> users = userRepository.findByCompanyId(companyId);

        java.util.stream.Stream<User> stream = users.stream();
        if (search != null && !search.trim().isEmpty()) {
            String q = search.trim().toLowerCase();
            stream = stream.filter(u -> 
                (u.getName() != null && u.getName().toLowerCase().contains(q)) || 
                u.getEmail().toLowerCase().contains(q)
            );
        }

        if (role != null && !role.trim().isEmpty() && !"ALL".equalsIgnoreCase(role)) {
            stream = stream.filter(u -> u.getRole() != null && u.getRole().name().equalsIgnoreCase(role));
        }

        List<User> filtered = stream.collect(java.util.stream.Collectors.toList());

        if (sort != null && !sort.trim().isEmpty()) {
            String[] parts = sort.split(",");
            String field = parts[0];
            boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
            filtered.sort((u1, u2) -> {
                int comp = 0;
                if ("name".equalsIgnoreCase(field)) {
                    String n1 = u1.getName() != null ? u1.getName() : "";
                    String n2 = u2.getName() != null ? u2.getName() : "";
                    comp = n1.compareToIgnoreCase(n2);
                } else if ("email".equalsIgnoreCase(field)) {
                    comp = u1.getEmail().compareToIgnoreCase(u2.getEmail());
                } else {
                    LocalDateTime t1 = u1.getCreatedAt() != null ? u1.getCreatedAt() : LocalDateTime.MIN;
                    LocalDateTime t2 = u2.getCreatedAt() != null ? u2.getCreatedAt() : LocalDateTime.MIN;
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
                User u = filtered.get(i);
                content.add(com.convexa.ai.convexa_ai_backend.dto.EmployeeResponse.builder()
                    .id(u.getId())
                    .name(u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail())
                    .email(u.getEmail())
                    .role(u.getRole() != null ? u.getRole().name() : "USER")
                    .department(u.getDepartment())
                    .createdAt(u.getCreatedAt())
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