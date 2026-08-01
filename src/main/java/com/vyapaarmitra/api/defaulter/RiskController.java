package com.vyapaarmitra.api.defaulter;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.defaulter.DefaulterDtos.RiskView;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exact-match risk lookup: given a full phone number a merchant is about to extend credit to,
 * returns only whether it's flagged on the network. There is intentionally NO list/search
 * endpoint — a flagged phone is discoverable only by typing it in full. See
 * web docs/defaulter-network.md.
 */
@RestController
@RequestMapping("/api/v1/risk")
@Validated
public class RiskController {

    private final DefaulterService defaulterService;

    public RiskController(DefaulterService defaulterService) {
        this.defaulterService = defaulterService;
    }

    @GetMapping
    public RiskView check(@AuthenticationPrincipal AuthUser authUser,
                          @RequestParam @NotBlank String phone) {
        return new RiskView(defaulterService.isPhoneFlagged(authUser.id(), phone));
    }
}
