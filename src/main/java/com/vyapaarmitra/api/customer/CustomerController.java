package com.vyapaarmitra.api.customer;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.customer.CustomerDtos.CreateCustomerRequest;
import com.vyapaarmitra.api.customer.CustomerDtos.CustomerListItem;
import com.vyapaarmitra.api.customer.CustomerDtos.CustomerResponse;
import com.vyapaarmitra.api.customer.CustomerDtos.UpdateCustomerRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public PageResponse<CustomerListItem> list(@AuthenticationPrincipal AuthUser authUser,
                                               @RequestParam(required = false) UUID branchId,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return customerService.list(authUser, branchId, q, page, size);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@AuthenticationPrincipal AuthUser authUser,
                                @PathVariable UUID id) {
        return customerService.get(authUser, id);
    }

    @PostMapping
    public CustomerResponse create(@AuthenticationPrincipal AuthUser authUser,
                                   @Valid @RequestBody CreateCustomerRequest request) {
        return customerService.create(authUser, request);
    }

    @PatchMapping("/{id}")
    public CustomerResponse update(@AuthenticationPrincipal AuthUser authUser,
                                   @PathVariable UUID id,
                                   @RequestBody UpdateCustomerRequest request) {
        return customerService.update(authUser, id, request);
    }
}
