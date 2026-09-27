package com.aigateway.controller;

import com.aigateway.dto.request.LoginRequest;
import com.aigateway.dto.request.RegisterRequest;
import com.aigateway.dto.response.AuthResponse;
import com.aigateway.dto.response.UserResponse;
import com.aigateway.exception.EmailAlreadyExistsException;
import com.aigateway.exception.InvalidCredentialsException;
import com.aigateway.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for AuthController — tests delegation logic only.
 *
 * Note: @WebMvcTest was removed in Spring Boot 4.x.
 * Full HTTP-layer tests can be done with @SpringBootTest + MockMvc when
 * integration test infrastructure (DB, Redis) is available.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock  AuthService   authService;
    @InjectMocks AuthController authController;

    private static final UserResponse USER_RESPONSE = UserResponse.builder()
            .id(1L).email("user@example.com")
            .build();

    private static final AuthResponse AUTH_RESPONSE = AuthResponse.builder()
            .accessToken("access.jwt")
            .refreshToken("1:uuid")
            .tokenType("Bearer")
            .expiresIn(900L)
            .build();

    // ── /register ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("register()")
    class RegisterTests {

        @Test @DisplayName("success → 201 Created with UserResponse")
        void register_201() {
            RegisterRequest req = new RegisterRequest("user@example.com", "Password123");
            when(authService.register(req)).thenReturn(USER_RESPONSE);

            ResponseEntity<UserResponse> response = authController.register(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getEmail()).isEqualTo("user@example.com");
        }

        @Test @DisplayName("duplicate email → EmailAlreadyExistsException propagated")
        void register_duplicateEmail() {
            RegisterRequest req = new RegisterRequest("user@example.com", "Password123");
            when(authService.register(req)).thenThrow(new EmailAlreadyExistsException("user@example.com"));

            assertThatThrownBy(() -> authController.register(req))
                    .isInstanceOf(EmailAlreadyExistsException.class);
        }
    }

    // ── /login ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("login()")
    class LoginTests {

        @Test @DisplayName("success → 200 OK with AuthResponse")
        void login_200() {
            LoginRequest req = new LoginRequest("user@example.com", "Password123");
            when(authService.login(req)).thenReturn(AUTH_RESPONSE);

            ResponseEntity<AuthResponse> response = authController.login(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getAccessToken()).isEqualTo("access.jwt");
            assertThat(response.getBody().getRefreshToken()).isEqualTo("1:uuid");
        }

        @Test @DisplayName("invalid credentials → InvalidCredentialsException propagated")
        void login_invalidCredentials() {
            LoginRequest req = new LoginRequest("user@example.com", "wrong");
            when(authService.login(req)).thenThrow(new InvalidCredentialsException());

            assertThatThrownBy(() -> authController.login(req))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    // ── /refresh ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("refresh()")
    class RefreshTests {

        @Test @DisplayName("success → 200 OK with new AuthResponse")
        void refresh_200() {
            var req = new com.aigateway.dto.request.RefreshTokenRequest("1:old-uuid");
            when(authService.refresh(any())).thenReturn(AUTH_RESPONSE);

            ResponseEntity<AuthResponse> response = authController.refresh(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getRefreshToken()).isEqualTo("1:uuid");
        }
    }

    // ── /logout ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("logout()")
    class LogoutTests {

        @Test @DisplayName("success → 204 No Content")
        void logout_204() {
            var req = new com.aigateway.dto.request.LogoutRequest("1:uuid");

            ResponseEntity<Void> response = authController.logout(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }
}
