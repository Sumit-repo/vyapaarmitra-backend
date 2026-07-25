package com.vyapaarmitra.api.invoice;

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

@WebMvcTest(controllers = InvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
class InvoiceControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceService invoiceService;

    @MockitoBean
    private JwtService jwtService;

    private static String body(String items, String extra) {
        return "{\"branchId\":\"" + UUID.randomUUID() + "\",\"billType\":\"KACCHA\","
            + "\"paymentMode\":\"CASH\",\"items\":" + items + extra + "}";
    }

    @Test
    void createRejectsEmptyItems() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("[]", "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.items").exists());
    }

    @Test
    void createRejectsMissingBillType() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + UUID.randomUUID()
                    + "\",\"paymentMode\":\"CASH\",\"items\":[{\"name\":\"Dal\",\"qty\":1,\"rate\":100}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.billType").exists());
    }

    @Test
    void createRejectsItemWithBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("[{\"name\":\"\",\"qty\":1,\"rate\":100}]", "")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsTaxRateAboveMax() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + UUID.randomUUID()
                    + "\",\"billType\":\"PAKKA\",\"paymentMode\":\"CASH\","
                    + "\"items\":[{\"name\":\"Rice\",\"qty\":1,\"rate\":100,\"taxRate\":40}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createAcceptsValidKacchaBill() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("[{\"name\":\"Toor Dal\",\"qty\":5,\"unit\":\"kg\",\"rate\":140}]",
                    ",\"amountReceived\":700")))
            .andExpect(status().isCreated());
    }

    @Test
    void listRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/invoices").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
