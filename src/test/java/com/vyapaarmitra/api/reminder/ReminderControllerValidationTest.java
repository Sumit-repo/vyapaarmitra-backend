package com.vyapaarmitra.api.reminder;

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

@WebMvcTest(controllers = ReminderController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReminderControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReminderService reminderService;

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
}
