package com.aigateway.controller;

import com.aigateway.config.SecurityConfig;
import com.aigateway.dto.response.AuthResponse;
import com.aigateway.dto.response.UserResponse;
import com.aigateway.enums.UserRole;
import com.aigateway.exception.EmailAlreadyExistsException;
import com.aigateway.exception.GlobalExceptionHandler;
import com.aigateway.exception.InvalidCredentialsException;
import com.aigateway.exception.InvalidTokenException;
import com.aigateway.security.JwtAuthenticationFilter;
import com.aigateway.security.JwtService;
import com.aigateway.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AuthControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @MockitoBean AuthService             authService;
    @MockitoBean JwtService              jwtService;
    @MockitoBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN    = "/api/v1/auth/login";
    private static final String REFRESH  = "/api/v1/auth/refresh";
    private static final String LOGOUT   = "/api/v1/auth/logout";

    // ── /register ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /register")
    class RegisterTests {

        @Test @DisplayName("201 on valid registration, no passwordHash in response")
        void register_201() throws Exception {
            UserResponse resp = UserResponse.builder()
                    .id(1L).email("user@example.com")
                    .role(UserRole.USER).enabled(true).build();

            when(authService.register(any())).thenReturn(resp);

            mockMvc.perform(post(REGISTER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"user@example.com","password":"Password123"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("user@example.com"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test @DisplayName("409 on duplicate email")
        void register_409_duplicateEmail() throws Exception {
            when(authService.register(any()))
                    .thenThrow(new EmailAlreadyExistsException("user@example.com"));

            mockMvc.perform(post(REGISTER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"user@example.com","password":"Password123"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test @DisplayName("400 on invalid email format")
        void register_400_badEmail() throws Exception {
            mockMvc.perform(post(REGISTER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"not-an-email","password":"Password123"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("400 on password shorter than 8 chars")
        void register_400_shortPassword() throws Exception {
            mockMvc.perform(post(REGISTER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"user@example.com","password":"short"}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── /login ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /login")
    class LoginTests {

        @Test @DisplayName("200 on valid credentials with tokens")
        void login_200() throws Exception {
            AuthResponse resp = AuthResponse.builder()
                    .accessToken("access.jwt")
                    .refreshToken("1:uuid")
                    .tokenType("Bearer")
                    .expiresIn(900L)
                    .build();

            when(authService.login(any())).thenReturn(resp);

            mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"user@example.com","password":"Password123"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                    .andExpect(jsonPath("$.refreshToken").value("1:uuid"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900));
        }

        @Test @DisplayName("401 on invalid credentials")
        void login_401_invalidCredentials() throws Exception {
            when(authService.login(any())).thenThrow(new InvalidCredentialsException());

            mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"user@example.com","password":"Wrong!123"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    // ── /refresh ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /refresh")
    class RefreshTests {

        @Test @DisplayName("200 on valid refresh token, new tokens returned")
        void refresh_200() throws Exception {
            AuthResponse resp = AuthResponse.builder()
                    .accessToken("new.access.jwt")
                    .refreshToken("1:new-uuid")
                    .tokenType("Bearer")
                    .expiresIn(900L)
                    .build();

            when(authService.refresh(any())).thenReturn(resp);

            mockMvc.perform(post(REFRESH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"1:old-uuid"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new.access.jwt"))
                    .andExpect(jsonPath("$.refreshToken").value("1:new-uuid"));
        }

        @Test @DisplayName("401 when refresh token not in Redis")
        void refresh_401_revokedToken() throws Exception {
            when(authService.refresh(any()))
                    .thenThrow(new InvalidTokenException("Refresh token is invalid or has expired"));

            mockMvc.perform(post(REFRESH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"1:revoked-uuid"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    // ── /logout ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /logout")
    class LogoutTests {

        @Test @DisplayName("204 on valid logout")
        void logout_204() throws Exception {
            mockMvc.perform(post(LOGOUT)
                            .header("Authorization", "Bearer access.jwt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"1:uuid"}
                                    """))
                    .andExpect(status().isNoContent());

            verify(authService).logout(any());
        }
    }
}
