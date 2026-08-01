package com.convexa.ai.convexa_ai_backend;

import com.convexa.ai.convexa_ai_backend.dto.RegisterRequest;
import com.convexa.ai.convexa_ai_backend.dto.AuthResponse;
import com.convexa.ai.convexa_ai_backend.dto.AcceptInvitationRequest;
import com.convexa.ai.convexa_ai_backend.dto.InvitationRequest;
import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.*;
import com.convexa.ai.convexa_ai_backend.service.UserService;
import com.convexa.ai.convexa_ai_backend.service.InvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MultiWorkspaceStage1Tests {

    @Autowired
    private UserService userService;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Test
    void testRegistrationDualWrites() {
        String email = "test.owner." + System.currentTimeMillis() + "@convexa.test";
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("Password@123");
        req.setName("Test Owner");
        req.setCompanyName("Dual Write Company");

        AuthResponse auth = userService.register(req);
        assertNotNull(auth.getToken());

        // Validate Legacy User Fields
        Optional<User> userOpt = userRepository.findByEmail(email);
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        assertEquals("Test Owner", user.getName());
        assertEquals(Role.OWNER, user.getRole());
        assertNotNull(user.getCompany());
        assertEquals("Dual Write Company", user.getCompany().getCompanyName());
        assertNotNull(user.getCompany().getCompanySlug());

        // Validate Company Entity
        Company company = user.getCompany();
        assertEquals(CompanyStatus.ACTIVE, company.getStatus());

        // Validate Shadow Membership Dual-Write
        Optional<OrganizationMembership> membershipOpt = 
                organizationMembershipRepository.findByUserIdAndCompanyId(user.getId(), company.getId());
        assertTrue(membershipOpt.isPresent());
        
        OrganizationMembership membership = membershipOpt.get();
        assertEquals(Role.OWNER, membership.getRole());
        assertEquals(MembershipStatus.ACTIVE, membership.getStatus());
        assertEquals(user.getId(), membership.getCreatedBy());
        assertNotNull(membership.getJoinedAt());
        assertNotNull(membership.getLastActivatedAt());
        assertNotNull(membership.getVersion());
    }
}
