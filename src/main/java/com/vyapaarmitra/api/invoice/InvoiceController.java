package com.vyapaarmitra.api.invoice;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.invoice.InvoiceDtos.CreateInvoiceRequest;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceListItem;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceListItem> list(@AuthenticationPrincipal AuthUser authUser,
                                              @RequestParam(required = false) UUID branchId,
                                              @RequestParam(required = false) BillType type,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return invoiceService.list(authUser, branchId, type, q, page, size);
    }

    @GetMapping("/invoices/{id}")
    public InvoiceResponse get(@AuthenticationPrincipal AuthUser authUser, @PathVariable UUID id) {
        return invoiceService.get(authUser, id);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@AuthenticationPrincipal AuthUser authUser,
                                  @Valid @RequestBody CreateInvoiceRequest request) {
        return invoiceService.create(authUser, request);
    }

    @PostMapping("/invoices/{id}/mark-paid")
    public InvoiceResponse markPaid(@AuthenticationPrincipal AuthUser authUser, @PathVariable UUID id) {
        return invoiceService.markPaid(authUser, id);
    }
}
