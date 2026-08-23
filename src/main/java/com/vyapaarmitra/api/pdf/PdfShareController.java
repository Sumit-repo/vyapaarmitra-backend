package com.vyapaarmitra.api.pdf;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.invoice.InvoiceService;
import com.vyapaarmitra.api.records.RecordsService;
import com.vyapaarmitra.api.recovery.RecoveryService;
import com.vyapaarmitra.api.subscription.PlanCatalog.Feature;
import com.vyapaarmitra.api.subscription.PlanGuard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renders a document, uploads it to Cloudinary (raw), and returns a public share URL. Mobile
 * shares this over WhatsApp (bills) or email (reports); the streaming {@code .../pdf} endpoints
 * stay for the authenticated web view. Same feature gates as the underlying data.
 */
@RestController
@RequestMapping("/api/v1")
public class PdfShareController {

    private final InvoiceService invoiceService;
    private final RecordsService recordsService;
    private final RecoveryService recoveryService;
    private final PdfStorage pdfStorage;
    private final PlanGuard planGuard;

    public PdfShareController(InvoiceService invoiceService, RecordsService recordsService,
                              RecoveryService recoveryService, PdfStorage pdfStorage, PlanGuard planGuard) {
        this.invoiceService = invoiceService;
        this.recordsService = recordsService;
        this.recoveryService = recoveryService;
        this.pdfStorage = pdfStorage;
        this.planGuard = planGuard;
    }

    @GetMapping("/invoices/{id}/pdf/link")
    public PdfLink billLink(@AuthenticationPrincipal AuthUser authUser, @PathVariable UUID id) {
        return new PdfLink(pdfStorage.upload(invoiceService.pdf(authUser, id), "bill-" + id));
    }

    @GetMapping("/records/statement/pdf/link")
    public PdfLink statementLink(@AuthenticationPrincipal AuthUser authUser,
                                 @RequestParam(required = false) UUID branchId,
                                 @RequestParam(required = false) UUID customerId,
                                 @RequestParam(defaultValue = "1") @Min(1) @Max(6) int months) {
        // Per-party statement (customerId) is a core khata action; only the shop-wide report is gated.
        if (customerId == null) {
            planGuard.requireFeature(authUser, Feature.REPORTS, "reports");
        }
        return new PdfLink(pdfStorage.upload(recordsService.statementPdf(authUser, branchId, customerId, months),
            "statement-" + (customerId != null ? customerId : authUser.businessId())));
    }

    @GetMapping("/recovery/overdue/pdf/link")
    public PdfLink overdueLink(@AuthenticationPrincipal AuthUser authUser,
                               @RequestParam(required = false) UUID branchId) {
        planGuard.requireFeature(authUser, Feature.RECOVERY, "recovery");
        return new PdfLink(pdfStorage.upload(recoveryService.overduePdf(authUser, branchId),
            "overdue-" + authUser.businessId()));
    }
}
