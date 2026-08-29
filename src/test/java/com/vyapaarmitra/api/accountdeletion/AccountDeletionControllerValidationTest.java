package com.vyapaarmitra.api.accountdeletion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AccountDeletionController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountDeletionControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountDeletionService service;

    // Component-scanned JwtAuthFilter needs a JwtService bean even though addFilters=false.
    @MockitoBean
    private JwtService jwtService;

    @Test
    void confirmRejectsBlankCode() throws Exception {
        mockMvc.perform(post("/api/v1/account/deletion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\",\"reason\":\"TOO_EXPENSIVE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.code").exists());
    }

    @Test
    void confirmRejectsTooLongFeedback() throws Exception {
        String feedback = "x".repeat(501);
        mockMvc.perform(post("/api/v1/account/deletion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\",\"reason\":\"OTHER\",\"feedback\":\"" + feedback + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.feedback").exists());
    }

    @Test
    void confirmRejectsUnknownReason() throws Exception {
        mockMvc.perform(post("/api/v1/account/deletion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\",\"reason\":\"NOPE\"}"))
            .andExpect(status().isBadRequest());
    }

    // §10: unknown JSON properties are rejected (FAIL_ON_UNKNOWN_PROPERTIES) so a typo'd field
    // can't silently pass.
    @Test
    void confirmRejectsUnknownJsonProperty() throws Exception {
        mockMvc.perform(post("/api/v1/account/deletion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\",\"reason\":\"OTHER\",\"typoField\":\"oops\"}"))
            .andExpect(status().isBadRequest());
    }
}
