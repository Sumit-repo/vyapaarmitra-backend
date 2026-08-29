package com.vyapaarmitra.api.accountdeletion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.JwtService;
import com.vyapaarmitra.api.config.AppProperties;
import com.vyapaarmitra.api.accountdeletion.AccountPurgeService.PurgeResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;

/**
 * §10/§11: {@code POST /internal/accounts/purge} requires the {@code X-Internal-Token} header (or
 * OIDC); missing/wrong → 401. The path is {@code permitAll} in the security chain so this header
 * check is the only gate. Verified as a slice test.
 */
@WebMvcTest(controllers = InternalPurgeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(InternalPurgeControllerTest.TestProps.class)
class InternalPurgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountPurgeService purgeService;

    // Component-scanned JwtAuthFilter needs a JwtService bean even though addFilters=false.
    @MockitoBean
    private JwtService jwtService;

    /** Supplies AppProperties with a configured purge token to the controller under test. */
    static class TestProps {
        @org.springframework.context.annotation.Bean
        AppProperties appProperties() {
            AppProperties.Internal internal = new AppProperties.Internal("secret-token");
            return new AppProperties("Asia/Kolkata", "http://localhost:3000",
                new AppProperties.Jwt("secret", 30, 30),
                new AppProperties.Cors(java.util.List.of("http://localhost:3000")),
                new AppProperties.Bootstrap(null, null, "Owner", "Shop", "Main"),
                new AppProperties.Google(java.util.List.of()),
                new AppProperties.Mail("from", null, 10),
                internal);
        }
    }

    @Test
    void purgeRejectsMissingTokenWith401() throws Exception {
        mockMvc.perform(post("/api/v1/internal/accounts/purge")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void purgeRejectsWrongTokenWith401() throws Exception {
        mockMvc.perform(post("/api/v1/internal/accounts/purge")
                .header("X-Internal-Token", "wrong")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void purgeAcceptsCorrectToken() throws Exception {
        when(purgeService.purgeExpired()).thenReturn(new PurgeResult(0, 0));

        mockMvc.perform(post("/api/v1/internal/accounts/purge")
                .header("X-Internal-Token", "secret-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}