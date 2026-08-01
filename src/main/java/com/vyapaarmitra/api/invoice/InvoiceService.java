package com.vyapaarmitra.api.invoice;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.invoice.InvoiceDtos.CreateInvoiceRequest;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceListItem;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;
import com.vyapaarmitra.api.invoice.InvoiceDtos.ItemRequest;
import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.ledger.LedgerDtos.CreateEntryRequest;
import com.vyapaarmitra.api.ledger.LedgerService;
import com.vyapaarmitra.api.user.UserDirectory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final InvoiceRepository invoiceRepository;
    private final BranchAccessService branchAccessService;
    private final LedgerService ledgerService;
    private final UserDirectory userDirectory;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          BranchAccessService branchAccessService,
                          LedgerService ledgerService,
                          UserDirectory userDirectory) {
        this.invoiceRepository = invoiceRepository;
        this.branchAccessService = branchAccessService;
        this.ledgerService = ledgerService;
        this.userDirectory = userDirectory;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceListItem> list(AuthUser authUser, UUID branchId, BillType type,
                                              String q, int page, int size) {
        Set<UUID> branchIds = branchAccessService.scope(authUser, branchId);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        Page<Invoice> result;
        if (q != null && !q.isBlank()) {
            result = invoiceRepository.search(branchIds, q.trim(), pageable);
        } else if (type != null) {
            result = invoiceRepository.findByBranchIdInAndBillTypeOrderByCreatedAtDesc(branchIds, type, pageable);
        } else {
            result = invoiceRepository.findByBranchIdInOrderByCreatedAtDesc(branchIds, pageable);
        }
        return PageResponse.of(result.map(InvoiceListItem::from));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(AuthUser authUser, UUID id) {
        return respond(loadAccessible(authUser, id));
    }

    /** Wraps an invoice with its resolved creator name (business-local). */
    private InvoiceResponse respond(Invoice invoice) {
        return InvoiceResponse.from(invoice,
            userDirectory.name(invoice.getBusinessId(), invoice.getCreatedBy()));
    }

    @Transactional
    public InvoiceResponse create(AuthUser authUser, CreateInvoiceRequest request) {
        branchAccessService.assertBranchAccess(authUser, request.branchId());

        boolean interState = request.billType() == BillType.PAKKA && Boolean.TRUE.equals(request.interState());
        List<InvoiceMath.ItemInput> inputs = request.items().stream()
            .map(InvoiceService::toItemInput)
            .toList();
        InvoiceMath.Totals totals = InvoiceMath.compute(
            request.billType(), interState, request.discount(), inputs);

        BigDecimal received = request.paymentMode() == PaymentMode.CREDIT
            ? BigDecimal.ZERO
            : clamp(request.amountReceived(), totals.grandTotal());
        BigDecimal balanceDue = totals.grandTotal().subtract(received).max(BigDecimal.ZERO);

        Invoice invoice = new Invoice();
        invoice.setBusinessId(authUser.businessId());
        invoice.setBranchId(request.branchId());
        invoice.setBillType(request.billType());
        invoice.setNumber(nextNumber(request.branchId(), request.billType()));
        invoice.setPartyCustomerId(request.partyCustomerId());
        invoice.setPartyName(trimToNull(request.partyName()));
        invoice.setPartyPhone(normalizePhone(request.partyPhone()));
        invoice.setPartyGstin(upperToNull(request.partyGstin()));
        if (request.billType() == BillType.PAKKA) {
            invoice.setSellerGstin(upperToNull(request.sellerGstin()));
            invoice.setPlaceOfSupply(trimToNull(request.placeOfSupply()));
            invoice.setInterState(interState);
        }
        invoice.setItems(totals.items());
        invoice.setDiscount(totals.discount());
        invoice.setSubtotal(totals.subtotal());
        invoice.setTaxTotal(totals.taxTotal());
        invoice.setGrandTotal(totals.grandTotal());
        invoice.setAmountReceived(received);
        invoice.setPaymentMode(request.paymentMode());
        invoice.setStatus(InvoiceMath.deriveStatus(totals.grandTotal(), received));
        invoice.setNotes(trimToNull(request.notes()));
        invoice.setCreatedBy(authUser.id());
        invoiceRepository.save(invoice);

        // A credit balance on a linked customer becomes udhar on their khata.
        if (balanceDue.signum() > 0 && request.partyCustomerId() != null) {
            var created = ledgerService.createEntry(authUser, new CreateEntryRequest(
                request.partyCustomerId(), EntryType.CREDIT, balanceDue, "INVOICE",
                "Bill " + invoice.getNumber(), null));
            invoice.setLedgerEntryId(created.entry().id());
            invoiceRepository.save(invoice);
        }

        return respond(invoice);
    }

    /**
     * Marks a bill fully paid. If the bill's unpaid balance sits on a linked
     * customer's khata (a credit entry was raised), a matching PAYMENT entry is
     * posted to settle it, so the invoice and the ledger stay consistent.
     */
    @Transactional
    public InvoiceResponse markPaid(AuthUser authUser, UUID id) {
        Invoice invoice = loadAccessible(authUser, id);
        BigDecimal balanceDue = invoice.getGrandTotal().subtract(invoice.getAmountReceived()).max(BigDecimal.ZERO);
        if (balanceDue.signum() <= 0) {
            return respond(invoice);
        }
        if (invoice.getLedgerEntryId() != null && invoice.getPartyCustomerId() != null) {
            ledgerService.createEntry(authUser, new CreateEntryRequest(
                invoice.getPartyCustomerId(), EntryType.PAYMENT, balanceDue, "INVOICE",
                "Paid " + invoice.getNumber(), null));
        }
        invoice.setAmountReceived(invoice.getGrandTotal());
        invoice.setStatus(BillStatus.PAID);
        invoiceRepository.save(invoice);
        return respond(invoice);
    }

    @Transactional(readOnly = true)
    public Invoice loadAccessible(AuthUser authUser, UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
            .filter(i -> i.getBusinessId().equals(authUser.businessId()))
            .orElseThrow(() -> ApiException.notFound("Bill not found"));
        branchAccessService.assertBranchAccess(authUser, invoice.getBranchId());
        return invoice;
    }

    private String nextNumber(UUID branchId, BillType type) {
        long count = invoiceRepository.countByBranchIdAndBillType(branchId, type);
        String prefix = type == BillType.PAKKA ? "INV" : "EST";
        return String.format("%s-%04d", prefix, count + 1);
    }

    private static InvoiceMath.ItemInput toItemInput(ItemRequest r) {
        String unit = (r.unit() == null || r.unit().isBlank()) ? "pcs" : r.unit().trim();
        return new InvoiceMath.ItemInput(r.name().trim(), r.qty(), unit, r.rate(), trimToNull(r.hsn()), r.taxRate());
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal max) {
        BigDecimal v = value == null ? max : value;
        return v.max(BigDecimal.ZERO).min(max);
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static String upperToNull(String s) {
        String t = trimToNull(s);
        return t == null ? null : t.toUpperCase();
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.replaceAll("[\\s-]", "");
    }
}
