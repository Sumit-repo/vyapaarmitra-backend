package com.vyapaarmitra.api.defaulter;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.defaulter.DefaulterDtos.ConsentRequest;
import com.vyapaarmitra.api.defaulter.DefaulterDtos.ConsentView;
import com.vyapaarmitra.api.defaulter.DefaulterDtos.DefaulterReportView;
import com.vyapaarmitra.api.defaulter.DefaulterDtos.WarnRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/defaulter")
public class DefaulterController {

    private final DefaulterService defaulterService;

    public DefaulterController(DefaulterService defaulterService) {
        this.defaulterService = defaulterService;
    }

    /**
     * Send the defaulter warning for a 90+-days-overdue customer, starting the 7-day grace.
     * Owner/staff of the shop; the customer must belong to the caller's business.
     */
    @PostMapping("/warn")
    public DefaulterReportView warn(@AuthenticationPrincipal AuthUser authUser,
                                    @Valid @RequestBody WarnRequest request) {
        return defaulterService.warn(authUser, request.customerId());
    }

    /** Current defaulter-network consent for the signed-in identity. */
    @GetMapping("/consent")
    public ConsentView getConsent(@AuthenticationPrincipal AuthUser authUser) {
        return new ConsentView(defaulterService.isConsented(authUser.id()));
    }

    /** Opt in/out of the defaulter network (identity-level; applies to all the user's shops). */
    @PutMapping("/consent")
    public ConsentView setConsent(@AuthenticationPrincipal AuthUser authUser,
                                  @Valid @RequestBody ConsentRequest request) {
        return new ConsentView(defaulterService.setConsent(authUser.id(), request.consent()));
    }
}
