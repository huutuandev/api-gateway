package com.aigateway.service;

import com.aigateway.config.JwtProperties;
import com.aigateway.dto.request.LoginRequest;
import com.aigateway.dto.request.LogoutRequest;
import com.aigateway.dto.request.RefreshTokenRequest;
import com.aigateway.dto.request.RegisterRequest;
import com.aigateway.dto.response.AuthResponse;
import com.aigateway.dto.response.UserResponse;
import com.aigateway.entity.User;
import com.aigateway.enums.UserRole;
import com.aigateway.exception.EmailAlreadyExistsException;
import com.aigateway.exception.InvalidCredentialsException;
import com.aigateway.repository.UserRepository;
import com.aigateway.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository       userRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final RefreshTokenService  refreshTokenService;
    private final JwtProperties        jwtProperties;



    @Transactional
    public UserResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("User registered: userId={}", saved.getId());

        return toUserResponse(saved);
    }



    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed - email not found: {}", request.getEmail());
                    return new InvalidCredentialsException();
                });

        if (!user.getEnabled()) {
            log.warn("Login failed - user disabled: userId={}", user.getId());
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed - wrong password: userId={}", user.getId());
            throw new InvalidCredentialsException();
        }

        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        log.info("User login success: userId={}", user.getId());

        return buildAuthResponse(accessToken, refreshToken);
    }



    public AuthResponse refresh(RefreshTokenRequest request) {


        Long userId = refreshTokenService.validateAndExtractUserId(request.getRefreshToken());


        refreshTokenService.deleteRefreshToken(request.getRefreshToken());


        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.getEnabled()) {
            throw new InvalidCredentialsException();
        }


        String newAccessToken  = jwtService.generateAccessToken(user);
        String newRefreshToken = refreshTokenService.createRefreshToken(userId);

        log.info("Refresh token rotated: userId={}", userId);

        return buildAuthResponse(newAccessToken, newRefreshToken);
    }



    public void logout(LogoutRequest request) {
        refreshTokenService.deleteRefreshToken(request.getRefreshToken());
        log.info("User logout: refreshToken deleted");
    }



    private AuthResponse buildAuthResponse(String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                .build();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .build();
    }
}