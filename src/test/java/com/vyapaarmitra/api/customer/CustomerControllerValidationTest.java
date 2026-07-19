package com.vyapaarmitra.api.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void createRejectsMissingName() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details.name").exists());
    }

    @Test
    void createRejectsMissingBranchId() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ramesh\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.branchId").exists());
    }

    @Test
    void createRejectsInvalidPhone() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + UUID.randomUUID()
                    + "\",\"name\":\"Ramesh\",\"phone\":\"abc\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.phone").exists());
    }

    @Test
    void createAcceptsValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + UUID.randomUUID()
                    + "\",\"name\":\"Ramesh Kumar\",\"phone\":\"+91 98765-43210\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void listRejectsPageSizeOverLimit() throws Exception {
        mockMvc.perform(get("/api/v1/customers").param("size", "500"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listRejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/customers").param("page", "-1"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getRejectsInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/customers/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }
}
