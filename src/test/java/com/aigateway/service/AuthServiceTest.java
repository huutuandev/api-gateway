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
import com.aigateway.exception.InvalidTokenException;
import com.aigateway.repository.UserRepository;
import com.aigateway.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository      userRepository;
    @Mock PasswordEncoder     passwordEncoder;
    @Mock JwtService          jwtService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock JwtProperties       jwtProperties;

    @InjectMocks AuthService authService;

    private User enabledUser;

    @BeforeEach
    void setUp() {
        enabledUser = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("$2a$10$hashed")
                .role(UserRole.USER)
                .enabled(true)
                .build();
    }

    // ── register() ───────────────────────────────────────────────────────────

    @Nested @DisplayName("register()")
    class RegisterTests {

        @Test @DisplayName("success → UserResponse returned, password is hashed")
        void register_success() {
            RegisterRequest req = new RegisterRequest("user@example.com", "Password123");

            when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
            when(passwordEncoder.encode("Password123")).thenReturn("$2a$10$hashed");
            when(userRepository.save(any())).thenReturn(enabledUser);

            UserResponse resp = authService.register(req);

            assertThat(resp.getId()).isEqualTo(1L);
            assertThat(resp.getEmail()).isEqualTo("user@example.com");
            assertThat(resp.getRole()).isEqualTo(UserRole.USER);
            assertThat(resp.getEnabled()).isTrue();

            // Verify password was encoded before save
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash())
                    .isEqualTo("$2a$10$hashed")
                    .isNotEqualTo("Password123");
        }

        @Test @DisplayName("duplicate email → EmailAlreadyExistsException, no save")
        void register_duplicateEmail() {
            RegisterRequest req = new RegisterRequest("user@example.com", "Password123");
            when(userRepository.existsByEmail(req.getEmail())).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(EmailAlreadyExistsException.class);

            verify(userRepository, never()).save(any());
        }
    }

    // ── login() ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("login()")
    class LoginTests {

        private LoginRequest req;

        @BeforeEach
        void setUp() {
            req = new LoginRequest("user@example.com", "Password123");
        }

        @Test @DisplayName("success → AuthResponse with tokens")
        void login_success() {
            when(userRepository.findByEmail(req.getEmail()))
                    .thenReturn(Optional.of(enabledUser));
            when(passwordEncoder.matches("Password123", "$2a$10$hashed")).thenReturn(true);
            when(jwtService.generateAccessToken(enabledUser)).thenReturn("access.jwt.token");
            when(refreshTokenService.createRefreshToken(1L)).thenReturn("1:uuid");
            when(jwtProperties.getAccessTokenExpiration()).thenReturn(900_000L);

            AuthResponse resp = authService.login(req);

            assertThat(resp.getAccessToken()).isEqualTo("access.jwt.token");
            assertThat(resp.getRefreshToken()).isEqualTo("1:uuid");
            assertThat(resp.getExpiresIn()).isEqualTo(900L);
        }

        @Test @DisplayName("email not found → InvalidCredentialsException")
        void login_emailNotFound() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test @DisplayName("wrong password → InvalidCredentialsException")
        void login_wrongPassword() {
            when(userRepository.findByEmail(req.getEmail()))
                    .thenReturn(Optional.of(enabledUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test @DisplayName("disabled user → InvalidCredentialsException")
        void login_disabledUser() {
            User disabled = User.builder()
                    .id(2L).email("user@example.com")
                    .passwordHash("$2a$10$hashed")
                    .role(UserRole.USER).enabled(false).build();

            when(userRepository.findByEmail(req.getEmail()))
                    .thenReturn(Optional.of(disabled));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    // ── refresh() ────────────────────────────────────────────────────────────

    @Nested @DisplayName("refresh()")
    class RefreshTests {

        @Test @DisplayName("valid refresh token → new tokens issued, old deleted")
        void refresh_success() {
            RefreshTokenRequest req = new RefreshTokenRequest("1:old-uuid");

            when(refreshTokenService.validateAndExtractUserId("1:old-uuid")).thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(enabledUser));
            when(jwtService.generateAccessToken(enabledUser)).thenReturn("new.access.token");
            when(refreshTokenService.createRefreshToken(1L)).thenReturn("1:new-uuid");
            when(jwtProperties.getAccessTokenExpiration()).thenReturn(900_000L);

            AuthResponse resp = authService.refresh(req);

            verify(refreshTokenService).deleteRefreshToken("1:old-uuid");
            assertThat(resp.getAccessToken()).isEqualTo("new.access.token");
            assertThat(resp.getRefreshToken()).isEqualTo("1:new-uuid");
        }

        @Test @DisplayName("invalid/expired token → InvalidTokenException")
        void refresh_invalidToken() {
            RefreshTokenRequest req = new RefreshTokenRequest("invalid-token");

            when(refreshTokenService.validateAndExtractUserId("invalid-token"))
                    .thenThrow(new InvalidTokenException("Refresh token is invalid or has expired"));

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(InvalidTokenException.class);

            verify(refreshTokenService, never()).deleteRefreshToken(anyString());
        }
    }

    // ── logout() ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("logout()")
    class LogoutTests {

        @Test @DisplayName("logout → refresh token deleted from Redis")
        void logout_deletesRefreshToken() {
            LogoutRequest req = new LogoutRequest("1:uuid");

            authService.logout(req);

            verify(refreshTokenService).deleteRefreshToken("1:uuid");
        }
    }
}
