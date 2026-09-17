package com.convexa.ai.convexa_ai_backend;

import com.convexa.ai.convexa_ai_backend.controller.DealController;
import com.convexa.ai.convexa_ai_backend.dto.PipelineIntelligenceResponse;
import com.convexa.ai.convexa_ai_backend.dto.RevenueTargetRequest;
import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.CompanyStatus;
import com.convexa.ai.convexa_ai_backend.entity.Role;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.exception.GlobalExceptionHandler;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import com.convexa.ai.convexa_ai_backend.service.DealService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Validates real HTTP API responses for PATCH /api/company/revenue-target
 * covering Section 3 and Section 4 of Revenue Target input verification.
 */
@SpringBootTest
@Transactional
public class RevenueTargetHttpEndpointTest {

    @Autowired
    private DealController dealController;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DealService dealService;

    private MockMvc mockMvc;
    private Company testCompany;
    private User ownerUser;
    private WorkspacePrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        testCompany = Company.builder()
                .companyName("Endpoint Verification Corp " + System.currentTimeMillis())
                .companySlug("endpoint-" + System.currentTimeMillis())
                .status(CompanyStatus.ACTIVE)
                .build();
        testCompany = companyRepository.save(testCompany);

        ownerUser = User.builder()
                .name("Endpoint Owner")
                .email("owner-" + System.currentTimeMillis() + "@endpoint.com")
                .password("Password123!")
                .role(Role.OWNER)
                .company(testCompany)
                .build();
        ownerUser = userRepository.save(ownerUser);

        currentPrincipal = WorkspacePrincipal.builder()
                .userId(ownerUser.getId())
                .companyId(testCompany.getId())
                .role(Role.OWNER)
                .email(ownerUser.getEmail())
                .build();

        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(WorkspacePrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return currentPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(dealController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver)
                .build();
    }

    // 1. Input: 400 -> HTTP 200, target saved
    @Test
    void testTarget_400Accepted() throws Exception {
        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": 400, \"period\": \"QUARTERLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Revenue target updated successfully"))
                .andExpect(jsonPath("$.quarterlyRevenueTarget").value(400));

        Company updated = companyRepository.findById(testCompany.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("400.00").compareTo(updated.getQuarterlyRevenueTarget()));
    }

    // 2. Input: 400.50 -> HTTP 200, positive decimal accepted without rounding errors
    @Test
    void testTarget_400_50Accepted() throws Exception {
        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": 400.50, \"period\": \"QUARTERLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Revenue target updated successfully"))
                .andExpect(jsonPath("$.quarterlyRevenueTarget").value(400.50));

        Company updated = companyRepository.findById(testCompany.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("400.50").compareTo(updated.getQuarterlyRevenueTarget()));
    }

    // 3. Input: 999 -> HTTP 200, target saved
    @Test
    void testTarget_999Accepted() throws Exception {
        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": 999, \"period\": \"QUARTERLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quarterlyRevenueTarget").value(999));

        Company updated = companyRepository.findById(testCompany.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("999.00").compareTo(updated.getQuarterlyRevenueTarget()));
    }

    // 4. Input: 1500 -> HTTP 200, target saved
    @Test
    void testTarget_1500Accepted() throws Exception {
        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": 1500, \"period\": \"QUARTERLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quarterlyRevenueTarget").value(1500));

        Company updated = companyRepository.findById(testCompany.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1500.00").compareTo(updated.getQuarterlyRevenueTarget()));
    }

    // 5. Input: 0 -> HTTP 400 with friendly validation message
    @Test
    void testTarget_0RejectedWithHttp400() throws Exception {
        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": 0, \"period\": \"QUARTERLY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("greater than 0")));
    }

    // 6. Input: -100 -> HTTP 400 with friendly validation message
    @Test
    void testTarget_NegativeRejectedWithHttp400() throws Exception {
        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": -100, \"period\": \"QUARTERLY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("greater than 0")));
    }

    // 7. Input: null -> HTTP 400 with friendly validation message
    @Test
    void testTarget_NullRejectedWithHttp400() throws Exception {
        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": null, \"period\": \"QUARTERLY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("required")));
    }

    // 8. Input: blank/string value -> HTTP 400, not HTTP 500
    @Test
    void testTarget_BlankOrStringRejectedWithHttp400() throws Exception {
        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": \"invalid_string\", \"period\": \"QUARTERLY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    // 9. Verify monthly target storage vs quarterly target storage
    @Test
    void testMonthlyTargetSavedToMonthlyField() throws Exception {
        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": 250.75, \"period\": \"MONTHLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyRevenueTarget").value(250.75))
                .andExpect(jsonPath("$.revenueTargetPeriod").value("MONTHLY"));

        Company updated = companyRepository.findById(testCompany.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("250.75").compareTo(updated.getMonthlyRevenueTarget()));
        assertEquals("MONTHLY", updated.getRevenueTargetPeriod());

        // Also verify getPipelineIntelligence returns it
        PipelineIntelligenceResponse intel = dealService.getPipelineIntelligence(currentPrincipal, "this_month");
        assertEquals(0, new BigDecimal("250.75").compareTo(intel.getRevenueTarget()));
        assertEquals("ALIGNED", intel.getTargetAlignmentStatus());
    }

    // 10. Non-Owner/Admin role rejected with HTTP 403
    @Test
    void testTarget_UnauthorizedRoleRejectedWithHttp403() throws Exception {
        currentPrincipal = WorkspacePrincipal.builder()
                .userId(999L)
                .companyId(testCompany.getId())
                .role(Role.USER)
                .email("user@test.com")
                .build();

        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": 5000, \"period\": \"QUARTERLY\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Only workspace owners and administrators")));
    }

    // 11. Unauthenticated request (null principal) rejected with HTTP 401
    @Test
    void testTarget_NullPrincipalRejectedWithHttp401() throws Exception {
        currentPrincipal = null;

        mockMvc.perform(patch("/api/company/revenue-target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": 5000, \"period\": \"QUARTERLY\"}"))
                .andExpect(status().isUnauthorized());
    }
}
