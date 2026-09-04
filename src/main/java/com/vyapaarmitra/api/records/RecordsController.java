package com.vyapaarmitra.api.records;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.records.RecordsService.StatementResponse;
import com.vyapaarmitra.api.subscription.PlanCatalog.Feature;
import com.vyapaarmitra.api.subscription.PlanGuard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    /**
     * Statement (passbook) for the last N months. Shop-wide (branch-scoped or consolidated) and
     * REPORTS-gated by default; when {@code customerId} is given it's the per-party statement
     * behind the ledger 3-dot — a core khata action, so it's not paywalled.
     */
    @GetMapping("/statement")
    public StatementResponse statement(@AuthenticationPrincipal AuthUser authUser,
                                       @RequestParam(required = false) UUID branchId,
                                       @RequestParam(required = false) UUID customerId,
                                       @RequestParam(defaultValue = "1") @Min(1) @Max(6) int months) {
        if (customerId == null) {
            planGuard.requireFeature(authUser, Feature.REPORTS, "reports");
        }
        return recordsService.statement(authUser, branchId, customerId, months);
    }

    /** Statement as a PDF — app share + web view; same gating as the JSON view. */
    @GetMapping("/statement/pdf")
    public ResponseEntity<byte[]> statementPdf(@AuthenticationPrincipal AuthUser authUser,
                                               @RequestParam(required = false) UUID branchId,
                                               @RequestParam(required = false) UUID customerId,
                                               @RequestParam(defaultValue = "1") @Min(1) @Max(6) int months) {
        if (customerId == null) {
            planGuard.requireFeature(authUser, Feature.REPORTS, "reports");
        }
        byte[] pdf = recordsService.statementPdf(authUser, branchId, customerId, months);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename("statement.pdf").build().toString())
            .body(pdf);
    }
}
