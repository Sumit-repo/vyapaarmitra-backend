package com.vyapaarmitra.api.supplier;

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

@WebMvcTest(controllers = SupplierController.class)
@AutoConfigureMockMvc(addFilters = false)
class SupplierControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupplierService supplierService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void createRejectsMissingName() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details.name").exists());
    }

    @Test
    void createRejectsMissingBranchId() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Verma Traders\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.branchId").exists());
    }

    @Test
    void createRejectsInvalidPhone() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + UUID.randomUUID()
                    + "\",\"name\":\"Verma Traders\",\"phone\":\"abc\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.phone").exists());
    }

    @Test
    void createAcceptsValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + UUID.randomUUID()
                    + "\",\"name\":\"Verma Traders\",\"phone\":\"+91 98765-43210\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void entryRejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/v1/supplier-entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":\"" + UUID.randomUUID()
                    + "\",\"entryType\":\"CREDIT\",\"amount\":-50}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.amount").exists());
    }

    @Test
    void entryRejectsMissingType() throws Exception {
        mockMvc.perform(post("/api/v1/supplier-entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":\"" + UUID.randomUUID() + "\",\"amount\":100}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.entryType").exists());
    }

    @Test
    void listRejectsPageSizeOverLimit() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers").param("size", "500"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getRejectsInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }
}
