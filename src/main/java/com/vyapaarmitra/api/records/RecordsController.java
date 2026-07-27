package com.vyapaarmitra.api.records;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.records.RecordsService.StatementResponse;
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
@RequestMapping("/api/v1/records")
public class RecordsController {

    private final RecordsService recordsService;
    private final PlanGuard planGuard;

    public RecordsController(RecordsService recordsService, PlanGuard planGuard) {
        this.recordsService = recordsService;
        this.planGuard = planGuard;
    }

    /** Shop-wide statement (passbook) for the last N months, branch-scoped or consolidated. */
    @GetMapping("/statement")
    public StatementResponse statement(@AuthenticationPrincipal AuthUser authUser,
                                       @RequestParam(required = false) UUID branchId,
                                       @RequestParam(defaultValue = "1") @Min(1) @Max(6) int months) {
        planGuard.requireFeature(authUser, Feature.REPORTS, "reports");
        return recordsService.statement(authUser, branchId, months);
    }
}
