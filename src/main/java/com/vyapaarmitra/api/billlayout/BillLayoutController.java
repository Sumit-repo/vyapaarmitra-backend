package com.vyapaarmitra.api.billlayout;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.billlayout.BillLayoutDtos.BillLayoutView;
import com.vyapaarmitra.api.billlayout.BillLayoutDtos.PreviewRequest;
import com.vyapaarmitra.api.billlayout.BillLayoutDtos.UpsertBillLayoutRequest;
import com.vyapaarmitra.api.subscription.PlanCatalog.Feature;
import com.vyapaarmitra.api.subscription.PlanGuard;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bill design (Pro): the shop's chosen preset + toggles. GET is a free read so the
 * designer can render the paywall honestly; PUT and the preview are gated by the
 * controller whenever the request asks for anything Pro ({@code requiresPro}).
 */
@RestController
@RequestMapping("/api/v1/bill-layout")
public class BillLayoutController {

    private final BillLayoutService billLayoutService;
    private final PlanGuard planGuard;

    public BillLayoutController(BillLayoutService billLayoutService, PlanGuard planGuard) {
        this.billLayoutService = billLayoutService;
        this.planGuard = planGuard;
    }

    @GetMapping
    public BillLayoutView get(@AuthenticationPrincipal AuthUser authUser) {
        return billLayoutService.view(authUser.businessId());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER', 'BRANCH_MANAGER')")
    public BillLayoutView update(@AuthenticationPrincipal AuthUser authUser,
                                 @Valid @RequestBody UpsertBillLayoutRequest request) {
        if (BillLayoutDtos.requiresPro(request.layout(), request.options())) {
            planGuard.requireFeature(authUser, Feature.BILL_LAYOUTS, "layout");
        }
        return billLayoutService.upsert(authUser, request);
    }

    /**
     * Renders the sample bill in the requested design so the designer can preview live.
     * POST with a JSON body: the v2 options (alignments, accent, toggles, label, date
     * format) don't belong in a query string, and the body scales with future options.
     * Same gate as PUT: a FREE shop may only preview the defaults.
     */
    @PostMapping("/preview")
    public ResponseEntity<byte[]> preview(@AuthenticationPrincipal AuthUser authUser,
                                          @Valid @RequestBody PreviewRequest request) {
        BillLayoutOptions options = request.options() == null
            ? BillLayoutOptions.defaults() : request.options();
        if (BillLayoutDtos.requiresPro(request.layout(), options)) {
            planGuard.requireFeature(authUser, Feature.BILL_LAYOUTS, "layout");
        }
        byte[] pdf = billLayoutService.preview(request.layout(), options);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename("bill-preview.pdf").build().toString())
            .body(pdf);
    }
}