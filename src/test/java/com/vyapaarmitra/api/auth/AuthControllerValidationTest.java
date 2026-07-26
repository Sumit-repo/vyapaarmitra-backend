package com.vyapaarmitra.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private GoogleAuthService googleAuthService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void loginRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"secret123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details.email").exists());
    }

    @Test
    void loginRejectsBlankPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@shop.com\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.password").exists());
    }

    @Test
    void loginRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void loginAcceptsValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@shop.com\",\"password\":\"secret123\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessName\":\"Sharma Store\",\"ownerName\":\"Ramesh\","
                    + "\"email\":\"owner@shop.com\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.password").exists());
    }

    @Test
    void registerRejectsBlankBusinessName() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessName\":\"\",\"ownerName\":\"Ramesh\","
                    + "\"email\":\"owner@shop.com\",\"password\":\"secret123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.businessName").exists());
    }

    @Test
    void registerAcceptsValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessName\":\"Sharma Store\",\"ownerName\":\"Ramesh\","
                    + "\"email\":\"owner@shop.com\",\"password\":\"secret123\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void refreshRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.refreshToken").exists());
    }

    @Test
    void otpRequestRejectsMissingPurpose() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@shop.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.purpose").exists());
    }

    @Test
    void otpRequestRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nope\",\"purpose\":\"LOGIN\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.email").exists());
    }

    @Test
    void otpRequestAcceptsValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@shop.com\",\"purpose\":\"LOGIN\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void otpVerifyRejectsBlankCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@shop.com\",\"code\":\"\",\"purpose\":\"LOGIN\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.code").exists());
    }

    @Test
    void otpVerifyAcceptsValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@shop.com\",\"code\":\"123456\",\"purpose\":\"LOGIN\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void googleRejectsMissingIdToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.idToken").exists());
    }

    @Test
    void googleAcceptsValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"a.b.c\"}"))
            .andExpect(status().isOk());
    }
}
