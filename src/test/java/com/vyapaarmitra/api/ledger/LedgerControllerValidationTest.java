package com.vyapaarmitra.api.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.JwtService;
import com.vyapaarmitra.api.subscription.PlanGuard;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LedgerController.class)
@AutoConfigureMockMvc(addFilters = false)
class LedgerControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerService ledgerService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PlanGuard planGuard;

    @Test
    void entryRejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/v1/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + UUID.randomUUID()
                    + "\",\"entryType\":\"CREDIT\",\"amount\":-50}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.amount").exists());
    }

    @Test
    void entryRejectsTooManyDecimals() throws Exception {
        mockMvc.perform(post("/api/v1/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + UUID.randomUUID()
                    + "\",\"entryType\":\"CREDIT\",\"amount\":10.999}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.amount").exists());
    }

    @Test
    void entryRejectsMissingType() throws Exception {
        mockMvc.perform(post("/api/v1/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + UUID.randomUUID() + "\",\"amount\":100}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.entryType").exists());
    }

    @Test
    void entryRejectsUnknownEntryType() throws Exception {
        mockMvc.perform(post("/api/v1/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + UUID.randomUUID()
                    + "\",\"entryType\":\"LOAN\",\"amount\":100}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void entryAcceptsValidCredit() throws Exception {
        mockMvc.perform(post("/api/v1/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + UUID.randomUUID()
                    + "\",\"entryType\":\"CREDIT\",\"amount\":250.50,"
                    + "\"note\":\"copies + stationery\",\"dueDate\":\"2026-08-15\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void ledgerRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID() + "/ledger")
                .param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
