package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.*;
import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.*;
import com.convexa.ai.convexa_ai_backend.service.CompanyService;
import com.convexa.ai.convexa_ai_backend.service.InvitationService;
import com.convexa.ai.convexa_ai_backend.service.UserService;
import com.convexa.ai.convexa_ai_backend.service.SubscriptionService;
import com.convexa.ai.convexa_ai_backend.service.CloudinaryService;
import com.convexa.ai.convexa_ai_backend.service.CloudinaryService.CloudinaryUploadResult;
import com.convexa.ai.convexa_ai_backend.exception.SeatLimitExceededException;
import com.convexa.ai.convexa_ai_backend.exception.DuplicatePendingInvitationException;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Company Dashboard & Manager Workspace Actions Controller (Sprint 2.6).
 *
 * Restricts all paths under /api/company/** to MANAGER and ADMIN roles via Spring Security.
 */
@RestController
@RequestMapping("/api/company")
@CrossOrigin("*")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private UserService userService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private CoachingSessionRepository coachingSessionRepository;

    @Autowired
    private com.convexa.ai.convexa_ai_backend.service.DailyCompanyMetricsService dailyCompanyMetricsService;

    @Autowired
    private LearningAssignmentRepository learningAssignmentRepository;

    @Autowired
    private ManagerNoteRepository managerNoteRepository;

    @Autowired
    private ImprovementPlanRepository improvementPlanRepository;

    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Autowired
    private com.convexa.ai.convexa_ai_backend.service.MediaLibraryService mediaLibraryService;

    @GetMapping("/stats")
    public ResponseEntity<CompanyStatsResponse> getCompanyStats(
            @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(companyService.getCompanyStats(principal.getCompanyId(), range));
    }

    /**
     * GET /api/company/media-library
     *
     * Returns real, database-derived media library statistics for the authenticated
     * company workspace. No Cloudinary Admin API calls are made at request time.
     * Company isolation is enforced server-side via the JWT-resolved principal.
     */
    @GetMapping("/media-library")
    public ResponseEntity<?> getMediaLibrary(
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(mediaLibraryService.getMediaLibrary(principal));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to load media library: " + e.getMessage());
        }
    }

    /**
     * GET /api/company/alerts
     * Returns server-computed data-driven alerts for the company.
     */
    @GetMapping("/alerts")
    public ResponseEntity<?> getCompanyAlerts(
            @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null || principal.getCompanyId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            var stats = companyService.getCompanyStats(principal.getCompanyId(), range);
            return ResponseEntity.ok(stats.getAlerts() != null ? stats.getAlerts() : List.of());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to load alerts: " + e.getMessage());
        }
    }

    /**
     * GET /api/company/team-insights
     * Returns 6 server-computed Executive Team Insights for the company.
     */
    @GetMapping("/team-insights")
    public ResponseEntity<?> getTeamInsights(
            @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null || principal.getCompanyId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            var stats = companyService.getCompanyStats(principal.getCompanyId(), range);
            return ResponseEntity.ok(stats.getTeamInsights());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to load team insights: " + e.getMessage());
        }
    }

    @GetMapping("/employee/{id}")
    public ResponseEntity<EmployeeProfileResponse> getEmployeeProfile(
            @PathVariable Long id,
            @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(companyService.getEmployeeProfile(id, principal.getCompanyId(), range));
    }

    @GetMapping("/employees")
    public ResponseEntity<?> getAllEmployees(@AuthenticationPrincipal WorkspacePrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<OrganizationMembership> memberships = organizationMembershipRepository
                .findByCompanyIdAndStatus(principal.getCompanyId(), MembershipStatus.ACTIVE);

        List<Map<String, Object>> list = memberships.stream()
                .map(m -> {
                    User u = m.getUser();
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("name", u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail());
                    map.put("email", u.getEmail());
                    map.put("role", m.getRole() != null ? m.getRole().name() : "USER");
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentCompany(@AuthenticationPrincipal WorkspacePrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Company company = companyRepository.findById(principal.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));
        return ResponseEntity.ok(company);
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getCompanyProfile(@AuthenticationPrincipal WorkspacePrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Company company = companyRepository.findById(principal.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));
        return ResponseEntity.ok(company);
    }

    @PatchMapping("/profile")
    public ResponseEntity<?> updateCompanyProfile(
            @RequestBody CompanyProfileUpdateRequest req,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Only OWNER and ADMIN are allowed to edit Company Settings/Branding
        if (principal.getRole() != Role.OWNER && principal.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Only owners and admins can update company settings."));
        }

        Company company = companyRepository.findById(principal.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        if (req.getCompanyName() != null) company.setCompanyName(req.getCompanyName());
        if (req.getCompanyLogo() != null) {
            // Clean up logo value: store as null if blank (remove logo)
            company.setCompanyLogo(req.getCompanyLogo().isBlank() ? null : req.getCompanyLogo());
        }
        if (req.getIndustry() != null) company.setIndustry(req.getIndustry());
        if (req.getCompanySize() != null) company.setCompanySize(req.getCompanySize());
        if (req.getWebsite() != null) company.setWebsite(req.getWebsite());

        companyRepository.save(company);
        return ResponseEntity.ok(company);
    }

    @PostMapping("/logo")
    public ResponseEntity<?> uploadLogo(
            @RequestParam("logo") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Only OWNER and ADMIN are allowed to edit Company Settings/Branding
        if (principal.getRole() != Role.OWNER && principal.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Only owners and admins can upload the company logo."));
        }

        Company company = companyRepository.findById(principal.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // 1. Validate file type: PNG, JPG, JPEG, WEBP
        String contentType = file.getContentType();
        if (contentType == null || 
            (!contentType.equalsIgnoreCase("image/png") && 
             !contentType.equalsIgnoreCase("image/jpeg") && 
             !contentType.equalsIgnoreCase("image/jpg") && 
             !contentType.equalsIgnoreCase("image/webp"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file type. Only PNG, JPG, JPEG, and WEBP are allowed."));
        }

        // 2. Validate file size: Maximum 5 MB (5 * 1024 * 1024 bytes)
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "File size exceeds the maximum limit of 5 MB."));
        }

        try {
            // 3. Upload directly to Cloudinary using CloudinaryService
            CloudinaryUploadResult uploadResult = cloudinaryService.uploadImage(file);

            // 4. Store secure_url inside companyLogo field
            company.setCompanyLogo(uploadResult.secureUrl());
            companyRepository.save(company);

            return ResponseEntity.ok(company);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Logo upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/employee/{id}/coaching")
    public ResponseEntity<?> addCoachingSession(
            @PathVariable Long id,
            @RequestBody CoachingSessionRequest req,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User manager = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));
        User employee = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!organizationMembershipRepository.existsByUserIdAndCompanyId(employee.getId(), principal.getCompanyId())) {
            throw new RuntimeException("Unauthorized: Employee belongs to a different company.");
        }

        CoachingSession session = CoachingSession.builder()
                .employee(employee)
                .manager(manager)
                .sessionDate(req.getSessionDate())
                .sessionTime(req.getSessionTime())
                .reason(req.getReason())
                .priority(req.getPriority())
                .notes(req.getNotes())
                .status(req.getStatus() != null ? req.getStatus() : "Pending")
                .build();

        coachingSessionRepository.save(session);
        return ResponseEntity.ok(Map.of("message", "Coaching session saved successfully"));
    }

    @PostMapping("/employee/{id}/learning")
    public ResponseEntity<?> addLearningAssignment(
            @PathVariable Long id,
            @RequestBody LearningAssignmentRequest req,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User manager = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));
        User employee = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!organizationMembershipRepository.existsByUserIdAndCompanyId(employee.getId(), principal.getCompanyId())) {
            throw new RuntimeException("Unauthorized: Employee belongs to a different company.");
        }

        LearningAssignment assignment = LearningAssignment.builder()
                .employee(employee)
                .manager(manager)
                .moduleName(req.getModuleName())
                .deadline(req.getDeadline())
                .priority(req.getPriority())
                .status(req.getStatus() != null ? req.getStatus() : "Assigned")
                .build();

        learningAssignmentRepository.save(assignment);
        return ResponseEntity.ok(Map.of("message", "Learning module assigned successfully"));
    }

    @PostMapping("/employee/{id}/notes")
    public ResponseEntity<?> addManagerNote(
            @PathVariable Long id,
            @RequestBody ManagerNoteRequest req,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User manager = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));
        User employee = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!organizationMembershipRepository.existsByUserIdAndCompanyId(employee.getId(), principal.getCompanyId())) {
            throw new RuntimeException("Unauthorized: Employee belongs to a different company.");
        }

        ManagerNote note = ManagerNote.builder()
                .employee(employee)
                .manager(manager)
                .text(req.getText())
                .build();

        managerNoteRepository.save(note);
        return ResponseEntity.ok(Map.of("message", "Manager note saved successfully"));
    }

    @PostMapping("/employee/{id}/improvements")
    public ResponseEntity<?> createImprovementPlan(
            @PathVariable Long id,
            @RequestBody ImprovementPlanRequest req,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User manager = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));
        User employee = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!organizationMembershipRepository.existsByUserIdAndCompanyId(employee.getId(), principal.getCompanyId())) {
            throw new RuntimeException("Unauthorized: Employee belongs to a different company.");
        }

        ImprovementPlan plan = ImprovementPlan.builder()
                .employee(employee)
                .manager(manager)
                .targetQA(req.getTargetQA())
                .targetSentiment(req.getTargetSentiment())
                .deadline(req.getDeadline())
                .assignedModules(req.getAssignedModules())
                .progress(req.getProgress() != null ? req.getProgress() : 0)
                .status(req.getStatus() != null ? req.getStatus() : "Active")
                .build();

        improvementPlanRepository.save(plan);
        return ResponseEntity.ok(Map.of("message", "Improvement plan created successfully"));
    }

    @PostMapping("/invitations")
    public ResponseEntity<?> createInvitation(
            @Valid @RequestBody InvitationRequest req,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User manager = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        InvitationResponse invite = invitationService.createInvitation(manager, req);
        return ResponseEntity.ok(invite);
    }

    @GetMapping("/invitations")
    public ResponseEntity<?> getCompanyInvitations(@AuthenticationPrincipal WorkspacePrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User manager = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        return ResponseEntity.ok(invitationService.getInvitations(manager));
    }

    @DeleteMapping("/invitations/{id}")
    public ResponseEntity<?> cancelInvitation(
            @PathVariable Long id,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User manager = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        invitationService.cancelInvitation(manager, id);
        return ResponseEntity.ok(Map.of("message", "Invitation cancelled successfully"));
    }

    @GetMapping("/members")
    public ResponseEntity<PagedMembersResponse> getMembers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "search", defaultValue = "") String search,
            @RequestParam(value = "role", defaultValue = "") String role,
            @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Auto-sync seat count to match actual DB membership before returning members list.
        subscriptionService.syncSeatCount(principal.getCompanyId());

        PagedMembersResponse response = userService.getMembers(principal.getCompanyId(), page, size, search, role, sort);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sync-seats")
    public ResponseEntity<?> syncSeats(@AuthenticationPrincipal WorkspacePrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        int actualCount = subscriptionService.syncSeatCount(principal.getCompanyId());
        return ResponseEntity.ok(Map.of("message", "Seat count synchronized", "currentSeatCount", actualCount));
    }

    @PostMapping("/invitations/{id}/resend")
    public ResponseEntity<?> resendInvitation(
            @PathVariable Long id,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User actor = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("Actor not found"));

        InvitationResponse response = invitationService.resendInvitation(actor, id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/members/{id}/role")
    public ResponseEntity<?> updateMemberRole(
            @PathVariable Long id,
            @RequestBody RoleUpdateRequest req,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User actor = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("Actor not found"));

        Role newRole;
        try {
            newRole = Role.valueOf(req.getRole().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role specified"));
        }

        userService.updateMemberRole(principal.getCompanyId(), principal.getUserId(), id, newRole);
        return ResponseEntity.ok(Map.of("message", "Member role updated successfully"));
    }

    @DeleteMapping("/members/{id}")
    public ResponseEntity<?> removeMember(
            @PathVariable Long id,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        userService.removeMember(principal.getCompanyId(), principal.getUserId(), id);
        return ResponseEntity.ok(Map.of("message", "Member removed successfully"));
    }

    @GetMapping("/daily-metrics")
    public ResponseEntity<?> getDailyMetrics(
            @RequestParam(value = "range", defaultValue = "30d") String range,
            @AuthenticationPrincipal WorkspacePrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<DailyCompanyMetricsDTO> metrics = dailyCompanyMetricsService.getDailyMetrics(principal.getCompanyId(), range);
        return ResponseEntity.ok(metrics);
    }

    @ExceptionHandler(SeatLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleSeatLimitExceeded(SeatLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DuplicatePendingInvitationException.class)
    public ResponseEntity<Map<String, String>> handleDuplicatePendingInvitation(DuplicatePendingInvitationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
