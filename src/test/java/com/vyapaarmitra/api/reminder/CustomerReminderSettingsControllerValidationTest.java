package com.vyapaarmitra.api.reminder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.JwtService;
import com.vyapaarmitra.api.customer.CustomerController;
import com.vyapaarmitra.api.customer.CustomerService;
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
class CustomerReminderSettingsControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private ReminderSettingsService reminderSettingsService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void updateRejectsInvalidChannel() throws Exception {
        mockMvc.perform(put("/api/v1/customers/" + UUID.randomUUID() + "/reminder-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredChannel\":\"PIGEON\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.preferredChannel").exists());
    }

    @Test
    void updateRejectsOversizedNotes() throws Exception {
        String bigNote = "x".repeat(501);
        mockMvc.perform(put("/api/v1/customers/" + UUID.randomUUID() + "/reminder-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reminderNotes\":\"" + bigNote + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.reminderNotes").exists());
    }

    @Test
    void updateAcceptsValidSettings() throws Exception {
        mockMvc.perform(put("/api/v1/customers/" + UUID.randomUUID() + "/reminder-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"smsReminderEnabled\":true,\"preferredChannel\":\"SMS\","
                    + "\"autoScheduleEnabled\":false,\"reminderNotes\":\"Pay on 1st of month\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void getReminderSettingsCallsService() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID() + "/reminder-settings"))
            .andExpect(status().isOk());
    }

    @Test
    void getReminderMessageRejectsOversizedType() throws Exception {
        String longType = "x".repeat(51);
        mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID() + "/reminder-message")
                .param("type", longType))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getReminderMessageUsesDefaultType() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID() + "/reminder-message"))
            .andExpect(status().isOk());
    }
}
