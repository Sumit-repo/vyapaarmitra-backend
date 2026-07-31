package com.vyapaarmitra.api.business;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BusinessController.class)
@AutoConfigureMockMvc(addFilters = false)
class BusinessControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusinessRepository businessRepository;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void updateRejectsBlankName() throws Exception {
        mockMvc.perform(patch("/api/v1/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.name").exists());
    }

    @Test
    void updateRejectsMissingName() throws Exception {
        mockMvc.perform(patch("/api/v1/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.name").exists());
    }

    @Test
    void updateRejectsTooLongName() throws Exception {
        String name = "x".repeat(121);
        mockMvc.perform(patch("/api/v1/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.name").exists());
    }
}
