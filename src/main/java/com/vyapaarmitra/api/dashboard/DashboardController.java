package com.vyapaarmitra.api.dashboard;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.dashboard.DashboardService.CollectionPoint;
import com.vyapaarmitra.api.dashboard.DashboardService.SummaryResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(@AuthenticationPrincipal AuthUser authUser,
                                   @RequestParam(required = false) UUID branchId) {
        return dashboardService.summary(authUser, branchId);
    }

    /** Daily collected (payments) and given (credit) for the trend chart. */
    @GetMapping("/collections")
    public List<CollectionPoint> collections(@AuthenticationPrincipal AuthUser authUser,
                                             @RequestParam(required = false) UUID branchId,
                                             @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
        return dashboardService.collections(authUser, branchId, days);
    }
}
