package com.vyapaarmitra.api.template;

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

@WebMvcTest(controllers = TemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
class TemplateControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemplateService templateService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void createRejectsBlankBody() throws Exception {
        mockMvc.perform(post("/api/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"SMS\",\"category\":\"soft\",\"name\":\"Soft\",\"body\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.body").exists());
    }

    @Test
    void createRejectsOversizedBody() throws Exception {
        String longBody = "x".repeat(1001);
        mockMvc.perform(post("/api/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"SMS\",\"category\":\"soft\",\"name\":\"Soft\",\"body\":\""
                    + longBody + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.body").exists());
    }

    @Test
    void createRejectsUnknownChannel() throws Exception {
        mockMvc.perform(post("/api/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"EMAIL\",\"category\":\"soft\",\"name\":\"Soft\",\"body\":\"Hi\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void createAcceptsValidTemplate() throws Exception {
        mockMvc.perform(post("/api/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"WHATSAPP\",\"category\":\"soft_reminder\","
                    + "\"name\":\"Soft reminder\",\"body\":\"Namaste {{customer_name}}\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void renderRejectsMissingCustomerId() throws Exception {
        mockMvc.perform(post("/api/v1/templates/" + UUID.randomUUID() + "/render")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.customerId").exists());
    }
}
