package com.vyapaarmitra.api.billlayout;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.auth.JwtService;
import com.vyapaarmitra.api.billlayout.BillLayoutDtos.BillLayoutView;
import com.vyapaarmitra.api.subscription.PlanGuard;
import com.vyapaarmitra.api.subscription.PlanLimitException;
import com.vyapaarmitra.api.user.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Gate behaviour: the read is free, anything Pro is a 402 with reason "layout". The slice
 * doesn't load SecurityConfig, so role enforcement is asserted by the service/unit side
 * and {@code @PreAuthorize} is only kept on the controller for the full context.
 */
@WebMvcTest(controllers = BillLayoutController.class)
@AutoConfigureMockMvc(addFilters = false)
class BillLayoutControllerTest {

    private static final AuthUser OWNER =
        new AuthUser(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillLayoutService billLayoutService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PlanGuard planGuard;

    /** {@code @AuthenticationPrincipal AuthUser} needs a real principal in the holder. */
    private ResultActions asOwner(RequestBuilder request) throws Exception {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(OWNER, null));
        try {
            return mockMvc.perform(request);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static String body(String layout, String options) {
        return "{\"layout\":\"" + layout + "\",\"options\":" + options + "}";
    }

    private static final String DEFAULT_OPTIONS =
        "{\"showUpiQr\":false,\"showLogo\":false,\"footerNote\":null}";

    @Test
    void getReturnsTheDesignAndThePresetCatalog() throws Exception {
        when(billLayoutService.view(any())).thenReturn(
            BillLayoutView.of(BillPreset.CLASSIC, BillLayoutOptions.defaults(), null));

        asOwner(get("/api/v1/bill-layout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.layout").value("CLASSIC"))
            .andExpect(jsonPath("$.options.showUpiQr").value(false))
            .andExpect(jsonPath("$.options.showLogo").value(false))
            .andExpect(jsonPath("$.options.footerNote").value(nullValue()))
            .andExpect(jsonPath("$.logoUrl").value(nullValue()))
            .andExpect(jsonPath("$.available.length()").value(3))
            .andExpect(jsonPath("$.available[0].key").value("CLASSIC"))
            .andExpect(jsonPath("$.available[0].label").value("Classic"))
            .andExpect(jsonPath("$.available[0].pro").value(false))
            .andExpect(jsonPath("$.available[1].key").value("MINIMAL"))
            .andExpect(jsonPath("$.available[1].label").value("Minimal"))
            .andExpect(jsonPath("$.available[1].pro").value(true))
            .andExpect(jsonPath("$.available[2].key").value("BOLD"))
            .andExpect(jsonPath("$.available[2].pro").value(true));
    }

    @Test
    void putAcceptsClassicWithNoTogglesWithoutGating() throws Exception {
        when(billLayoutService.upsert(any(), any())).thenReturn(
            BillLayoutView.of(BillPreset.CLASSIC, BillLayoutOptions.defaults(), null));

        asOwner(put("/api/v1/bill-layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("CLASSIC", DEFAULT_OPTIONS)))
            .andExpect(status().isOk());

        verify(planGuard, never()).requireFeature(any(), any(), anyString());
    }

    @Test
    void putRejectsNonClassicPresetForFreePlan() throws Exception {
        doThrow(new PlanLimitException("layout", "Bill design needs Pro."))
            .when(planGuard).requireFeature(any(), any(), anyString());

        asOwner(put("/api/v1/bill-layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("MINIMAL", DEFAULT_OPTIONS)))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.error.code").value("PLAN_LIMIT"))
            .andExpect(jsonPath("$.error.reason").value("layout"));
    }

    @Test
    void putRejectsAToggleEvenOnClassic() throws Exception {
        doThrow(new PlanLimitException("layout", "Bill design needs Pro."))
            .when(planGuard).requireFeature(any(), any(), anyString());

        asOwner(put("/api/v1/bill-layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("CLASSIC", "{\"showUpiQr\":true,\"showLogo\":false,\"footerNote\":null}")))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.error.reason").value("layout"));
    }

    @Test
    void putRejectsARealFooterNote() throws Exception {
        doThrow(new PlanLimitException("layout", "Bill design needs Pro."))
            .when(planGuard).requireFeature(any(), any(), anyString());

        asOwner(put("/api/v1/bill-layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("CLASSIC", "{\"showUpiQr\":false,\"showLogo\":false,"
                    + "\"footerNote\":\"Thank you!\"}")))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.error.reason").value("layout"));
    }

    @Test
    void putRejectsMissingOptions() throws Exception {
        asOwner(put("/api/v1/bill-layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"layout\":\"CLASSIC\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void putRejectsAnUnknownPreset() throws Exception {
        asOwner(put("/api/v1/bill-layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("FANCY", "{}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void previewServesPdfBytes() throws Exception {
        byte[] pdf = "%PDF-1.4 preview".getBytes();
        when(billLayoutService.preview(any(), anyBoolean(), anyBoolean(), any())).thenReturn(pdf);

        asOwner(get("/api/v1/bill-layout/preview")
                .param("layout", "MINIMAL").param("showUpiQr", "true"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", containsString("inline")))
            .andExpect(header().string("Content-Disposition", containsString("bill-preview.pdf")))
            .andExpect(content().string(startsWith("%PDF-")));
    }

    @Test
    void previewIsGatedForProDesigns() throws Exception {
        doThrow(new PlanLimitException("layout", "Bill design needs Pro."))
            .when(planGuard).requireFeature(any(), any(), anyString());

        asOwner(get("/api/v1/bill-layout/preview")
                .param("layout", "BOLD").param("showLogo", "true"))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.error.reason").value("layout"));
        verify(billLayoutService, never()).preview(any(), anyBoolean(), anyBoolean(), any());
    }
}