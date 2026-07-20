package com.vyapaarmitra.api.customer;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.customer.CustomerDtos.CreateCustomerRequest;
import com.vyapaarmitra.api.customer.CustomerDtos.CustomerListItem;
import com.vyapaarmitra.api.customer.CustomerDtos.CustomerResponse;
import com.vyapaarmitra.api.customer.CustomerDtos.UpdateCustomerRequest;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final BranchAccessService branchAccessService;

    public CustomerService(CustomerRepository customerRepository,
                           BranchAccessService branchAccessService) {
        this.customerRepository = customerRepository;
        this.branchAccessService = branchAccessService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerListItem> list(AuthUser authUser, UUID branchId, String q,
                                               int page, int size) {
        Set<UUID> branchIds = branchAccessService.scope(authUser, branchId);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        Page<Customer> result = (q == null || q.isBlank())
            ? customerRepository.findByBranchIdInAndActiveTrueOrderByNameAsc(branchIds, pageable)
            : customerRepository.search(branchIds, q.trim(), pageable);
        return PageResponse.of(result.map(CustomerListItem::from));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(AuthUser authUser, UUID customerId) {
        return CustomerResponse.from(loadAccessible(authUser, customerId));
    }

    @Transactional
    public CustomerResponse create(AuthUser authUser, CreateCustomerRequest request) {
        branchAccessService.assertBranchAccess(authUser, request.branchId());
        Customer customer = new Customer();
        customer.setBusinessId(authUser.businessId());
        customer.setBranchId(request.branchId());
        customer.setName(request.name().trim());
        customer.setPhone(normalizePhone(request.phone()));
        customer.setAddress(request.address());
        customer.setTags(request.tags() == null ? new ArrayList<>() : new ArrayList<>(request.tags()));
        customer.setNotes(request.notes());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(AuthUser authUser, UUID customerId,
                                   UpdateCustomerRequest request) {
        Customer customer = loadAccessible(authUser, customerId);
        if (request.name() != null && !request.name().isBlank()) {
            customer.setName(request.name().trim());
        }
        if (request.phone() != null) {
            customer.setPhone(normalizePhone(request.phone()));
        }
        if (request.address() != null) {
            customer.setAddress(request.address());
        }
        if (request.tags() != null) {
            customer.setTags(new ArrayList<>(request.tags()));
        }
        if (request.notes() != null) {
            customer.setNotes(request.notes());
        }
        if (request.active() != null) {
            customer.setActive(request.active());
        }
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Customer loadAccessible(AuthUser authUser, UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
            .filter(c -> c.getBusinessId().equals(authUser.businessId()))
            .orElseThrow(() -> ApiException.notFound("Customer not found"));
        branchAccessService.assertBranchAccess(authUser, customer.getBranchId());
        return customer;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.replaceAll("[\\s-]", "");
    }
}
