package com.convexa.ai.convexa_ai_backend.security;

import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.CompanyStatus;
import com.convexa.ai.convexa_ai_backend.entity.MembershipStatus;
import com.convexa.ai.convexa_ai_backend.entity.OrganizationMembership;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.repository.OrganizationMembershipRepository;
import com.convexa.ai.convexa_ai_backend.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Autowired(required = false)
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") 
                || path.startsWith("/api/invitations/") 
                || path.startsWith("/audio/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);
            String email = jwtService.extractEmail(token);
            Long userId = jwtService.extractUserId(token);

            if (email == null || userId == null) {
                writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Invalid or malformed JWT token.", request.getRequestURI());
                return;
            }

            request.setAttribute("userEmail", email);

            String uri = request.getRequestURI();
            boolean isWorkspaceScoped = uri.startsWith("/api/") 
                    && !uri.startsWith("/api/auth/") 
                    && !uri.startsWith("/api/invitations/") 
                    && !uri.startsWith("/api/settings")
                    && !uri.startsWith("/audio/")
                    && !uri.equals("/api/workspaces");

            if (isWorkspaceScoped) {
                String workspaceIdHeader = request.getHeader("X-Workspace-Id");
                if (workspaceIdHeader == null || workspaceIdHeader.trim().isEmpty()) {
                    writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Bad Request", "Missing required header: X-Workspace-Id", request.getRequestURI());
                    return;
                }

                Long companyId;
                try {
                    companyId = Long.valueOf(workspaceIdHeader.trim());
                } catch (NumberFormatException e) {
                    writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Bad Request", "Invalid header format: X-Workspace-Id must be a numeric value.", request.getRequestURI());
                    return;
                }

                // Verify membership against database
                Optional<OrganizationMembership> membershipOpt = 
                        organizationMembershipRepository.findByUserIdAndCompanyId(userId, companyId);

                if (membershipOpt.isEmpty()) {
                    writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "User does not have a membership in the requested workspace.", request.getRequestURI());
                    return;
                }

                OrganizationMembership membership = membershipOpt.get();
                if (membership.getStatus() != MembershipStatus.ACTIVE) {
                    writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "Workspace access is inactive or suspended.", request.getRequestURI());
                    return;
                }

                Optional<Company> companyOpt = companyRepository.findById(companyId);
                if (companyOpt.isEmpty() || (companyOpt.get().getStatus() != null && companyOpt.get().getStatus() != CompanyStatus.ACTIVE)) {
                    writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "Workspace is archived or inactive.", request.getRequestURI());
                    return;
                }

                // Populate SecurityContext with custom WorkspacePrincipal and Roles
                WorkspacePrincipal principal = WorkspacePrincipal.builder()
                        .userId(userId)
                        .companyId(companyId)
                        .role(membership.getRole())
                        .email(email)
                        .build();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + membership.getRole().name()))
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } else {
                // Identity authenticated but not active workspace scoped request (e.g. workspace listing switcher)
                WorkspacePrincipal principal = WorkspacePrincipal.builder()
                        .userId(userId)
                        .companyId(null)
                        .role(null)
                        .email(email)
                        .build();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of()
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

        } catch (Exception e) {
            log.error("JWT Filter exception for URI {}: ", request.getRequestURI(), e);
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Invalid or expired JWT token.", request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            int statusCode,
            String errorType,
            String message,
            String path
    ) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "status", statusCode,
                "error", errorType,
                "message", message,
                "path", path
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}