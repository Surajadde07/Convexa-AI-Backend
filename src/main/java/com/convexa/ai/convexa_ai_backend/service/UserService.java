package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.AuthResponse;
import com.convexa.ai.convexa_ai_backend.dto.LoginRequest;
import com.convexa.ai.convexa_ai_backend.dto.RegisterRequest;
import com.convexa.ai.convexa_ai_backend.entity.Role;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // =========================
    // REGISTER USER
    // =========================

    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {

            throw new RuntimeException(
                    "Email already registered"
            );
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(
                        passwordEncoder.encode(
                                request.getPassword()
                        )
                )
                .role(Role.USER)
                .build();

        User savedUser =
                userRepository.save(user);

        String token =
                jwtService.generateToken(
                        savedUser.getEmail()
                );

        return buildAuthResponse(savedUser, token, "Registration successful");
    }

    // =========================
    // LOGIN USER
    // =========================

    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Invalid email or password"
                        )
                );

        boolean passwordMatches =
                passwordEncoder.matches(
                        request.getPassword(),
                        user.getPassword()
                );

        if (!passwordMatches) {

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }

        String token =
                jwtService.generateToken(
                        user.getEmail()
                );

        return buildAuthResponse(user, token, "Login successful");
    }

    public AuthResponse buildAuthResponse(User user, String token, String message) {
        String companyName = null;
        String companyLogo = null;
        String managerName = "System Manager";

        if (user.getCompany() != null) {
            companyName = user.getCompany().getCompanyName();
            companyLogo = user.getCompany().getCompanyLogo();
            if (companyLogo == null || companyLogo.isBlank()) {
                companyLogo = "https://via.placeholder.com/150?text=Convexa+AI";
            }
            
            List<User> companyUsers = userRepository.findByCompanyId(user.getCompany().getId());
            for (User cu : companyUsers) {
                if (cu.getRole() == Role.MANAGER || cu.getRole() == Role.ADMIN) {
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
                .companyLogo(companyLogo)
                .department(user.getDepartment())
                .managerName(managerName)
                .build();
    }
}