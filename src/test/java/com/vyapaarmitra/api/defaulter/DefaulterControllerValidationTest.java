package com.vyapaarmitra.api.defaulter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@WebMvcTest(controllers = DefaulterController.class)
@AutoConfigureMockMvc(addFilters = false)
class DefaulterControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DefaulterService defaulterService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void warnRejectsMissingCustomerId() throws Exception {
        mockMvc.perform(post("/api/v1/defaulter/warn")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.customerId").exists());
    }

    @Test
    void setConsentRejectsMissingFlag() throws Exception {
        mockMvc.perform(put("/api/v1/defaulter/consent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.consent").exists());
    }
}
