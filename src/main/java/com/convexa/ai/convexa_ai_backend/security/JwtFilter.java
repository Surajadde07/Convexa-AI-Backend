package com.convexa.ai.convexa_ai_backend.security;

import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader =
                request.getHeader("Authorization");

        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        try {

            String token =
                    authHeader.substring(7);

            String email =
                    jwtService.extractEmail(token);

            request.setAttribute(
                    "userEmail",
                    email
            );

            // Role is deliberately NOT stored in the JWT itself (see Sprint 1.5
            // notes) — reading it fresh from the DB on every request means a
            // promotion/demotion takes effect on the user's very next request,
            // not "after they log out and back in". Every controller in this
            // app already re-fetches the User by email per-request anyway, so
            // this is the same query pattern, not new overhead.
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid Token");
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            List.of(
                                    new SimpleGrantedAuthority(
                                            "ROLE_" + user.getRole().name()
                                    )
                            )
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(
                            authentication
                    );

        } catch (Exception e) {

            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
            );

            response.getWriter()
                    .write("Invalid Token");

            return;
        }

        filterChain.doFilter(
                request,
                response
        );
    }
}