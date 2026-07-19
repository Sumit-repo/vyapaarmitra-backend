package com.vyapaarmitra.api.reminder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.JwtService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReminderController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReminderControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReminderService reminderService;

    @MockitoBean
    private ReminderSettingsService reminderSettingsService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void createRejectsMissingOutcome() throws Exception {
        mockMvc.perform(post("/api/v1/reminders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.outcome").exists());
    }

    @Test
    void createRejectsInvalidChannel() throws Exception {
        mockMvc.perform(post("/api/v1/reminders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + UUID.randomUUID()
                    + "\",\"outcome\":\"REMINDER_SENT\",\"channel\":\"PIGEON\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.channel").exists());
    }

    @Test
    void createAcceptsValidReminder() throws Exception {
        mockMvc.perform(post("/api/v1/reminders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + UUID.randomUUID()
                    + "\",\"outcome\":\"PROMISE_MADE\",\"channel\":\"WHATSAPP\","
                    + "\"promisedDate\":\"2026-07-25\",\"note\":\"will pay after salary\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void dueReturnsListFromService() throws Exception {
        Mockito.when(reminderSettingsService.getDueReminders(Mockito.any(), Mockito.any()))
            .thenReturn(List.of());
        mockMvc.perform(get("/api/v1/reminders/due"))
            .andExpect(status().isOk());
    }

    @Test
    void promptedAcceptsEmptyBody() throws Exception {
        mockMvc.perform(post("/api/v1/reminders/customers/" + UUID.randomUUID() + "/prompted")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());
    }

    @Test
    void sentRejectsOversizedType() throws Exception {
        String longType = "x".repeat(51);
        mockMvc.perform(post("/api/v1/reminders/customers/" + UUID.randomUUID() + "/sent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + longType + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.type").exists());
    }

    @Test
    void sentAcceptsValidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/reminders/customers/" + UUID.randomUUID() + "/sent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment_due\",\"note\":\"SMS sent via android composer\"}"))
            .andExpect(status().isNoContent());
    }
}
