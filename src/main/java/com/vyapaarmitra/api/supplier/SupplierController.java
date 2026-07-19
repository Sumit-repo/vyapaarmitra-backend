package com.vyapaarmitra.api.supplier;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.supplier.SupplierDtos.CreateSupplierEntryRequest;
import com.vyapaarmitra.api.supplier.SupplierDtos.CreateSupplierRequest;
import com.vyapaarmitra.api.supplier.SupplierDtos.SupplierEntryCreatedResponse;
import com.vyapaarmitra.api.supplier.SupplierDtos.SupplierEntryResponse;
import com.vyapaarmitra.api.supplier.SupplierDtos.SupplierListItem;
import com.vyapaarmitra.api.supplier.SupplierDtos.SupplierResponse;
import com.vyapaarmitra.api.supplier.SupplierDtos.UpdateSupplierRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping("/suppliers")
    public PageResponse<SupplierListItem> list(@AuthenticationPrincipal AuthUser authUser,
                                               @RequestParam(required = false) UUID branchId,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return supplierService.list(authUser, branchId, q, page, size);
    }

    @GetMapping("/suppliers/{id}")
    public SupplierResponse get(@AuthenticationPrincipal AuthUser authUser, @PathVariable UUID id) {
        return supplierService.get(authUser, id);
    }

    @PostMapping("/suppliers")
    public SupplierResponse create(@AuthenticationPrincipal AuthUser authUser,
                                   @Valid @RequestBody CreateSupplierRequest request) {
        return supplierService.create(authUser, request);
    }

    @PatchMapping("/suppliers/{id}")
    public SupplierResponse update(@AuthenticationPrincipal AuthUser authUser,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody UpdateSupplierRequest request) {
        return supplierService.update(authUser, id, request);
    }

    @GetMapping("/suppliers/{id}/ledger")
    public PageResponse<SupplierEntryResponse> ledger(@AuthenticationPrincipal AuthUser authUser,
                                                      @PathVariable UUID id,
                                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return supplierService.ledger(authUser, id, page, size);
    }

    @PostMapping("/supplier-entries")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierEntryCreatedResponse createEntry(@AuthenticationPrincipal AuthUser authUser,
                                                    @Valid @RequestBody CreateSupplierEntryRequest request) {
        return supplierService.createEntry(authUser, request);
    }
}
