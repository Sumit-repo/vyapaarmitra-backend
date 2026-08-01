package com.vyapaarmitra.api.supplier;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.ledger.LedgerMath;
import com.vyapaarmitra.api.supplier.SupplierDtos.CreateSupplierEntryRequest;
import com.vyapaarmitra.api.supplier.SupplierDtos.CreateSupplierRequest;
import com.vyapaarmitra.api.supplier.SupplierDtos.SupplierEntryCreatedResponse;
import com.vyapaarmitra.api.supplier.SupplierDtos.SupplierEntryResponse;
import com.vyapaarmitra.api.supplier.SupplierDtos.SupplierListItem;
import com.vyapaarmitra.api.supplier.SupplierDtos.SupplierResponse;
import com.vyapaarmitra.api.supplier.SupplierDtos.UpdateSupplierRequest;
import com.vyapaarmitra.api.user.UserDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_CREDIT_TERM_DAYS = 30;

    private final SupplierRepository supplierRepository;
    private final SupplierLedgerEntryRepository entryRepository;
    private final BranchAccessService branchAccessService;
    private final UserDirectory userDirectory;
    private final AppTime appTime;

    public SupplierService(SupplierRepository supplierRepository,
                           SupplierLedgerEntryRepository entryRepository,
                           BranchAccessService branchAccessService,
                           UserDirectory userDirectory,
                           AppTime appTime) {
        this.supplierRepository = supplierRepository;
        this.entryRepository = entryRepository;
        this.branchAccessService = branchAccessService;
        this.userDirectory = userDirectory;
        this.appTime = appTime;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierListItem> list(AuthUser authUser, UUID branchId, String q,
                                               int page, int size) {
        Set<UUID> branchIds = branchAccessService.scope(authUser, branchId);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        Page<Supplier> result = (q == null || q.isBlank())
            ? supplierRepository.findByBranchIdInAndActiveTrueOrderByNameAsc(branchIds, pageable)
            : supplierRepository.search(branchIds, q.trim(), pageable);
        return PageResponse.of(result.map(SupplierListItem::from));
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(AuthUser authUser, UUID supplierId) {
        return SupplierResponse.from(loadAccessible(authUser, supplierId));
    }

    @Transactional
    public SupplierResponse create(AuthUser authUser, CreateSupplierRequest request) {
        branchAccessService.assertBranchAccess(authUser, request.branchId());
        Supplier supplier = new Supplier();
        supplier.setBusinessId(authUser.businessId());
        supplier.setBranchId(request.branchId());
        supplier.setName(request.name().trim());
        supplier.setPhone(normalizePhone(request.phone()));
        supplier.setAddress(request.address());
        supplier.setTags(request.tags() == null ? new ArrayList<>() : new ArrayList<>(request.tags()));
        supplier.setNotes(request.notes());
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(AuthUser authUser, UUID supplierId, UpdateSupplierRequest request) {
        Supplier supplier = loadAccessible(authUser, supplierId);
        if (request.name() != null && !request.name().isBlank()) {
            supplier.setName(request.name().trim());
        }
        if (request.phone() != null) {
            supplier.setPhone(normalizePhone(request.phone()));
        }
        if (request.address() != null) {
            supplier.setAddress(request.address());
        }
        if (request.tags() != null) {
            supplier.setTags(new ArrayList<>(request.tags()));
        }
        if (request.notes() != null) {
            supplier.setNotes(request.notes());
        }
        if (request.active() != null) {
            supplier.setActive(request.active());
        }
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierEntryResponse> ledger(AuthUser authUser, UUID supplierId,
                                                      int page, int size) {
        Supplier supplier = loadAccessible(authUser, supplierId);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        Page<SupplierLedgerEntry> entries = entryRepository
            .findBySupplierIdOrderByEntryAtDesc(supplierId, pageable);
        List<SupplierLedgerEntry> content = entries.getContent();

        List<SupplierEntryResponse> items;
        if (content.isEmpty()) {
            items = List.of();
        } else {
            BigDecimal newerSum = entryRepository.signedSumAfter(supplierId, content.get(0).getEntryAt());
            BigDecimal balanceAfterTop = supplier.getCurrentBalance().subtract(newerSum);
            List<BigDecimal> signed = content.stream().map(SupplierService::signed).toList();
            List<BigDecimal> balances = LedgerMath.runningBalancesDesc(signed, balanceAfterTop);
            Map<UUID, String> names = userDirectory.namesByBusiness(supplier.getBusinessId(),
                content.stream().map(SupplierLedgerEntry::getCreatedBy).toList());
            items = new ArrayList<>(content.size());
            for (int i = 0; i < content.size(); i++) {
                SupplierLedgerEntry e = content.get(i);
                items.add(SupplierEntryResponse.from(e, balances.get(i), names.get(e.getCreatedBy())));
            }
        }
        return new PageResponse<>(items, entries.getNumber(), entries.getSize(),
            entries.getTotalElements(), entries.getTotalPages());
    }

    /** Signed contribution to the balance: credits add, payments subtract. */
    private static BigDecimal signed(SupplierLedgerEntry e) {
        return e.getEntryType() == EntryType.CREDIT ? e.getAmount() : e.getAmount().negate();
    }

    @Transactional
    public SupplierEntryCreatedResponse createEntry(AuthUser authUser, CreateSupplierEntryRequest request) {
        Supplier supplier = loadAccessible(authUser, request.supplierId());

        SupplierLedgerEntry entry = new SupplierLedgerEntry();
        entry.setBusinessId(supplier.getBusinessId());
        entry.setBranchId(supplier.getBranchId());
        entry.setSupplierId(supplier.getId());
        entry.setEntryType(request.entryType());
        entry.setAmount(request.amount());
        entry.setMethod(request.method());
        entry.setNote(request.note());
        if (request.entryType() == EntryType.CREDIT) {
            entry.setDueDate(request.dueDate() != null
                ? request.dueDate()
                : appTime.today().plusDays(DEFAULT_CREDIT_TERM_DAYS));
        }
        entry.setEntryAt(Instant.now());
        entry.setCreatedBy(authUser.id());
        entryRepository.save(entry);

        recomputeSupplierState(supplier);
        supplierRepository.save(supplier);

        // The new entry is the newest, so its running balance is the supplier's fresh balance.
        String createdByName = userDirectory.name(supplier.getBusinessId(), entry.getCreatedBy());
        return new SupplierEntryCreatedResponse(
            SupplierEntryResponse.from(entry, supplier.getCurrentBalance(), createdByName),
            SupplierResponse.from(supplier));
    }

    @Transactional(readOnly = true)
    public Supplier loadAccessible(AuthUser authUser, UUID supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
            .filter(s -> s.getBusinessId().equals(authUser.businessId()))
            .orElseThrow(() -> ApiException.notFound("Supplier not found"));
        branchAccessService.assertBranchAccess(authUser, supplier.getBranchId());
        return supplier;
    }

    /**
     * Recomputes the supplier's denormalized balance + oldest due date from the
     * full ledger. Payments allocate FIFO against credits, same as customers.
     * Suppliers have no trust score.
     */
    private void recomputeSupplierState(Supplier supplier) {
        List<SupplierLedgerEntry> entries = entryRepository
            .findBySupplierIdOrderByEntryAtAsc(supplier.getId());

        List<LedgerMath.CreditLine> credits = entries.stream()
            .filter(e -> e.getEntryType() == EntryType.CREDIT)
            .map(e -> new LedgerMath.CreditLine(e.getAmount(), e.getDueDate()))
            .toList();
        BigDecimal totalPayments = entries.stream()
            .filter(e -> e.getEntryType() == EntryType.PAYMENT)
            .map(SupplierLedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = appTime.today();
        LedgerMath.LedgerState state = LedgerMath.compute(credits, totalPayments, today);

        supplier.setCurrentBalance(state.balance());
        supplier.setOldestDueDate(state.balance().compareTo(BigDecimal.ZERO) > 0
            ? state.oldestOpenDueDate()
            : null);
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.replaceAll("[\\s-]", "");
    }
}
