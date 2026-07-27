package com.vyapaarmitra.api.recovery;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.recovery.RecoveryService.RecoveryItem;
import com.vyapaarmitra.api.subscription.PlanCatalog.Feature;
import com.vyapaarmitra.api.subscription.PlanGuard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recovery")
public class RecoveryController {

    private final RecoveryService recoveryService;
    private final PlanGuard planGuard;

    public RecoveryController(RecoveryService recoveryService, PlanGuard planGuard) {
        this.recoveryService = recoveryService;
        this.planGuard = planGuard;
    }

    /** "Who to contact today" — the daily follow-up list, branch-scoped or consolidated. */
    @GetMapping("/today")
    public PageResponse<RecoveryItem> today(@AuthenticationPrincipal AuthUser authUser,
                                            @RequestParam(required = false) UUID branchId,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        planGuard.requireFeature(authUser, Feature.RECOVERY, "recovery");
        return recoveryService.today(authUser, branchId, page, size);
    }
}
