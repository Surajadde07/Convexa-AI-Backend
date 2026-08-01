package com.convexa.ai.convexa_ai_backend.config;

import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.OrganizationMembershipRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class MembershipBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MembershipBackfillRunner.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Override
    public void run(String... args) {
        log.info("[MembershipBackfillRunner] Checking for companies needing status/slug repair...");
        List<Company> companies = companyRepository.findAll();
        for (Company c : companies) {
            boolean updated = false;
            if (c.getStatus() == null) {
                c.setStatus(CompanyStatus.ACTIVE);
                updated = true;
            }
            if (c.getCompanySlug() == null || c.getCompanySlug().trim().isEmpty()) {
                String baseSlug = (c.getCompanyName() != null ? c.getCompanyName() : "workspace")
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-+|-+$", "");
                if (baseSlug.isEmpty()) baseSlug = "workspace";
                c.setCompanySlug(baseSlug);
                updated = true;
            }
            if (updated) {
                companyRepository.save(c);
                log.info("[MembershipBackfillRunner] Repaired Company ID {}: status={}, slug={}", c.getId(), c.getStatus(), c.getCompanySlug());
            }
        }

        log.info("[MembershipBackfillRunner] Checking for users missing OrganizationMembership rows...");
        List<User> users = userRepository.findAll();
        int backfilledCount = 0;

        for (User u : users) {
            if (u.getCompany() != null) {
                boolean hasMembership = organizationMembershipRepository
                        .findByUserIdAndCompanyId(u.getId(), u.getCompany().getId())
                        .isPresent();

                if (!hasMembership) {
                    Role userRole = u.getRole() != null ? u.getRole() : Role.OWNER;
                    OrganizationMembership membership = OrganizationMembership.builder()
                            .user(u)
                            .company(u.getCompany())
                            .role(userRole)
                            .status(MembershipStatus.ACTIVE)
                            .joinedAt(LocalDateTime.now())
                            .lastActivatedAt(LocalDateTime.now())
                            .createdBy(u.getId())
                            .build();

                    organizationMembershipRepository.save(membership);
                    backfilledCount++;
                    log.info("[MembershipBackfillRunner] Created membership for User ID {} ({}) in Company ID {}", u.getId(), u.getEmail(), u.getCompany().getId());
                }
            }
        }

        log.info("[MembershipBackfillRunner] Migration complete — backfilled {} memberships.", backfilledCount);
    }
}
