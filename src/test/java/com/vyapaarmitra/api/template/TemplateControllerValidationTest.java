package com.vyapaarmitra.api.template;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.auth.JwtService;
import java.time.LocalDate;
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
                .content("{\"category\":\"soft\",\"name\":\"Soft\",\"body\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.body").exists());
    }

    @Test
    void createRejectsOversizedBody() throws Exception {
        String longBody = "x".repeat(1001);
        mockMvc.perform(post("/api/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"soft\",\"name\":\"Soft\",\"body\":\""
                    + longBody + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.body").exists());
    }

    @Test
    void createAcceptsValidTemplate() throws Exception {
        mockMvc.perform(post("/api/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"soft_reminder\","
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

    /** Window dates are optional (web renders without them) and parse as ISO yyyy-MM-dd. */
    @Test
    void renderAcceptsOptionalWindowDates() throws Exception {
        UUID templateId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(templateService.render(nullable(AuthUser.class), eq(templateId), eq(customerId),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
            .thenReturn(new TemplateDtos.RenderResponse(templateId, "Namaste"));

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/render")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + customerId + "\","
                    + "\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}"))
            .andExpect(status().isOk());
    }
}
