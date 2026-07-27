package com.vyapaarmitra.api.subscription;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.subscription.BillingDtos.CheckoutRequest;
import com.vyapaarmitra.api.subscription.BillingDtos.CheckoutResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /** Start a subscription; returns the hosted Razorpay checkout link. */
    @PostMapping("/checkout")
    public CheckoutResponse checkout(@AuthenticationPrincipal AuthUser authUser,
                                     @Valid @RequestBody CheckoutRequest request) {
        return billingService.checkout(authUser, request.plan(), request.period());
    }

    /** Cancel at period end. */
    @PostMapping("/portal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void portal(@AuthenticationPrincipal AuthUser authUser) {
        billingService.cancel(authUser);
    }
}
