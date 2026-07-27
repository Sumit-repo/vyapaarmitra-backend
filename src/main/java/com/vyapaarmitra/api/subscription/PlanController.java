package com.vyapaarmitra.api.subscription;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.subscription.PlanDtos.PlanView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Standalone plan read, mirroring the plan block on {@code /me}. */
@RestController
@RequestMapping("/api/v1")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/plan")
    public PlanView plan(@AuthenticationPrincipal AuthUser authUser) {
        return planService.view(authUser);
    }
}
