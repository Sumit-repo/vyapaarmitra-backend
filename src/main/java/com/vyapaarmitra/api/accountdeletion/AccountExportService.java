package com.vyapaarmitra.api.accountdeletion;

import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.invoice.InvoiceRepository;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.membership.MembershipRepository;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import com.vyapaarmitra.api.common.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A portable, bounded JSON dump of the caller's data (reciprocity + data ownership): the businesses
 * they own, plus each business's parties, ledger entries and bills. Every list is capped so a large
 * shop can't produce an unbounded response; the caps are generous enough to cover real khatas. See
 * docs/account-deletion.md §5 (GET /account/export).
 */
@Service
public class AccountExportService {

    // Per-business caps. Bills/entries are the high-cardinality rows, so they get the headroom.
    private static final Limit MAX_CUSTOMERS = Limit.of(20_000);
    private static final Limit MAX_LEDGER_ENTRIES = Limit.of(100_000);
    private static final Limit MAX_INVOICES = Limit.of(100_000);

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final InvoiceRepository invoiceRepository;

    public AccountExportService(UserRepository userRepository,
                                MembershipRepository membershipRepository,
                                BusinessRepository businessRepository,
                                CustomerRepository customerRepository,
                                LedgerEntryRepository ledgerEntryRepository,
                                InvoiceRepository invoiceRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.businessRepository = businessRepository;
        this.customerRepository = customerRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.invoiceRepository = invoiceRepository;
    }

    public record ExportFile(UUID userId, String email, Instant exportedAt,
                             List<BusinessExport> businesses) {
    }

    public record BusinessExport(UUID id, String name, List<PartyExport> parties,
                                 List<LedgerExport> ledger, List<BillExport> bills) {
    }

    public record PartyExport(UUID id, String name, String phone, String address,
                              BigDecimal currentBalance) {
    }

    public record LedgerExport(UUID id, UUID customerId, String entryType, BigDecimal amount,
                               String method, String note, Instant entryAt) {
    }

    public record BillExport(UUID id, String number, String billType, String partyName,
                             String partyPhone, BigDecimal grandTotal, String status,
                             Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public ExportFile export(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));

        List<UUID> ownedBusinessIds = membershipRepository.findByUserIdAndActiveTrue(userId).stream()
            .filter(m -> m.getRole() == Role.OWNER)
            .map(Membership::getBusinessId)
            .toList();

        List<BusinessExport> businesses = ownedBusinessIds.stream()
            .map(this::exportBusiness)
            .filter(java.util.Objects::nonNull)
            .toList();

        return new ExportFile(user.getId(), user.getEmail(), Instant.now(), businesses);
    }

    private BusinessExport exportBusiness(UUID businessId) {
        Business business = businessRepository.findById(businessId).orElse(null);
        if (business == null) {
            return null;
        }
        List<PartyExport> parties = customerRepository
            .findByBusinessIdOrderByCreatedAtAsc(businessId, MAX_CUSTOMERS).stream()
            .map(c -> new PartyExport(c.getId(), c.getName(), c.getPhone(), c.getAddress(),
                c.getCurrentBalance()))
            .toList();
        List<LedgerExport> ledger = ledgerEntryRepository
            .findByBusinessIdOrderByEntryAtAsc(businessId, MAX_LEDGER_ENTRIES).stream()
            .map(e -> new LedgerExport(e.getId(), e.getCustomerId(),
                e.getEntryType() == null ? null : e.getEntryType().name(), e.getAmount(),
                e.getMethod(), e.getNote(), e.getEntryAt()))
            .toList();
        List<BillExport> bills = invoiceRepository
            .findByBusinessIdOrderByCreatedAtAsc(businessId, MAX_INVOICES).stream()
            .map(i -> new BillExport(i.getId(), i.getNumber(),
                i.getBillType() == null ? null : i.getBillType().name(), i.getPartyName(),
                i.getPartyPhone(), i.getGrandTotal(),
                i.getStatus() == null ? null : i.getStatus().name(), i.getCreatedAt()))
            .toList();
        return new BusinessExport(business.getId(), business.getName(), parties, ledger, bills);
    }
}
