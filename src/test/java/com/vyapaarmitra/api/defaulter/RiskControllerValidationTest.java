package com.vyapaarmitra.api.defaulter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RiskController.class)
@AutoConfigureMockMvc(addFilters = false)
class RiskControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DefaulterService defaulterService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void rejectsMissingPhone() throws Exception {
        mockMvc.perform(get("/api/v1/risk"))
            .andExpect(status().isBadRequest());
    }
}
