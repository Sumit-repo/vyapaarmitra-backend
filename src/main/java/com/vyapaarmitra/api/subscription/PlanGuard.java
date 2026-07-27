package com.vyapaarmitra.api.subscription;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.subscription.PlanCatalog.Entitlements;
import com.vyapaarmitra.api.subscription.PlanCatalog.Feature;
import com.vyapaarmitra.api.subscription.PlanDtos.Usage;
import org.springframework.stereotype.Component;

/**
 * The single enforcement seam for plan limits. Controllers call these before the
 * work happens; every rejection is a {@link PlanLimitException} (402) whose reason
 * matches the web's UpgradeReason. Kept thin and boolean so it's obvious what each
 * tier gates.
 */
@Component
public class PlanGuard {

    private final PlanService planService;

    public PlanGuard(PlanService planService) {
        this.planService = planService;
    }

    /** Metered caps: entries/day and (for pakka bills) GST bills/month. */
    public void assertCanCreateEntry(AuthUser authUser, boolean pakka) {
        Entitlements ent = planService.entitlements(authUser.businessId());
        Usage usage = planService.usage(authUser.businessId());
        if (pakka && usage.pakkaThisMonth() >= ent.pakkaMonthlyCap()) {
            throw new PlanLimitException("gst", "Monthly GST bill limit reached for your plan.");
        }
        if (usage.entriesToday() >= ent.dailyEntryCap()) {
            throw new PlanLimitException("daily", "Daily entry limit reached for your plan.");
        }
    }

    /** Feature lock: reason is the web UpgradeReason (e.g. "recovery", "reports", "staff"). */
    public void requireFeature(AuthUser authUser, Feature feature, String reason) {
        if (!planService.entitlements(authUser.businessId()).has(feature)) {
            throw new PlanLimitException(reason, "This feature isn't available on your plan.");
        }
    }

    /** Branch cap: reject when the business already runs its allotted number of branches. */
    public void assertCanAddBranch(AuthUser authUser, long currentBranchCount) {
        int max = planService.entitlements(authUser.businessId()).maxBranches();
        if (currentBranchCount >= max) {
            throw new PlanLimitException("branches", "Branch limit reached for your plan.");
        }
    }
}
