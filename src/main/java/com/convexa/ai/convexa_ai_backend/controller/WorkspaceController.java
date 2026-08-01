package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.Subscription;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.OrganizationMembershipRepository;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @GetMapping
    public ResponseEntity<?> getUserWorkspaces(@AuthenticationPrincipal WorkspacePrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        List<Company> companies = organizationMembershipRepository.findActiveCompaniesByUserId(principal.getUserId());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Company c : companies) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("name", c.getCompanyName() != null ? c.getCompanyName() : "Workspace");
            item.put("slug", c.getCompanySlug() != null ? c.getCompanySlug() : "workspace");
            item.put("logoUrl", c.getCompanyLogo() != null && !c.getCompanyLogo().isBlank() ? c.getCompanyLogo() : "https://via.placeholder.com/150?text=Convexa+AI");
            list.add(item);
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentWorkspace(@AuthenticationPrincipal WorkspacePrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        if (principal.getCompanyId() == null) {
            return ResponseEntity.status(400).body(Map.of("message", "No active workspace context in request header."));
        }

        Company company = companyRepository.findById(principal.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        Subscription sub = company.getSubscription();
        Map<String, Object> subscriptionMap = new HashMap<>();
        if (sub != null) {
            subscriptionMap.put("plan", sub.getPlan() != null ? sub.getPlan().name() : "BUSINESS");
            subscriptionMap.put("status", sub.getStatus() != null ? sub.getStatus().name() : "TRIALING");
            subscriptionMap.put("seatLimit", sub.getSeatLimit() != null ? sub.getSeatLimit() : 25);
            subscriptionMap.put("currentSeatCount", sub.getCurrentSeatCount() != null ? sub.getCurrentSeatCount() : 1);
        } else {
            subscriptionMap.put("plan", "BUSINESS");
            subscriptionMap.put("status", "TRIALING");
            subscriptionMap.put("seatLimit", 25);
            subscriptionMap.put("currentSeatCount", 1);
        }

        Map<String, Object> brandingMap = new HashMap<>();
        brandingMap.put("primaryColor", company.getBrandPrimaryColor() != null ? company.getBrandPrimaryColor() : "#1A73E8");
        brandingMap.put("secondaryColor", company.getBrandSecondaryColor() != null ? company.getBrandSecondaryColor() : "#34A853");

        Map<String, Object> companyMap = new HashMap<>();
        companyMap.put("id", company.getId());
        companyMap.put("name", company.getCompanyName() != null ? company.getCompanyName() : "Workspace");
        companyMap.put("slug", company.getCompanySlug() != null ? company.getCompanySlug() : "workspace");
        companyMap.put("logoUrl", company.getCompanyLogo() != null && !company.getCompanyLogo().isBlank() ? company.getCompanyLogo() : "https://via.placeholder.com/150?text=Convexa+AI");
        companyMap.put("website", company.getWebsite() != null ? company.getWebsite() : "");
        companyMap.put("industry", company.getIndustry() != null ? company.getIndustry() : "");
        companyMap.put("companySize", company.getCompanySize() != null ? company.getCompanySize() : "");
        companyMap.put("branding", brandingMap);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("company", companyMap);
        responseMap.put("subscription", subscriptionMap);
        responseMap.put("role", principal.getRole() != null ? principal.getRole().name() : "OWNER");

        return ResponseEntity.ok(responseMap);
    }
}
