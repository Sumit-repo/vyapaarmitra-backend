package com.vyapaarmitra.api.dashboard;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.dashboard.DashboardService.SummaryResponse;
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
}
