package com.vyapaarmitra.api.share;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.auth.JwtService;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.invoice.Invoice;
import com.vyapaarmitra.api.invoice.InvoiceRepository;
import com.vyapaarmitra.api.invoice.InvoiceService;
import com.vyapaarmitra.api.ledger.LedgerDtos.EntryResponse;
import com.vyapaarmitra.api.ledger.LedgerService;
import com.vyapaarmitra.api.share.ShareDtos.ShareLedgerResponse;
import com.vyapaarmitra.api.share.ShareDtos.ShareMetaResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public share links (docs/customer-web-viewer.md). Shopkeepers create a stable, revocable
 * link to a customer's ledger or a bill; the customer opens it, enters the party's phone
 * last-4, and gets a short-lived view token that authorizes the data/PDF reads. Brute-force
 * of the 4-digit factor is contained by a graduated lockout on the link itself.
 */
@Service
public class ShareService {

    private static final String ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LEN = 22;        // ~128 bits over base62
    private static final int LEDGER_WINDOW = 100;   // bounded public ledger (design decision #3)

    private final SecureRandom random = new SecureRandom();

    private final ShareLinkRepository shareLinkRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final BusinessRepository businessRepository;
    private final LedgerService ledgerService;
    private final InvoiceService invoiceService;
    private final JwtService jwtService;

    public ShareService(ShareLinkRepository shareLinkRepository, CustomerRepository customerRepository,
                        InvoiceRepository invoiceRepository, BusinessRepository businessRepository,
                        LedgerService ledgerService, InvoiceService invoiceService, JwtService jwtService) {
        this.shareLinkRepository = shareLinkRepository;
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.businessRepository = businessRepository;
        this.ledgerService = ledgerService;
        this.invoiceService = invoiceService;
        this.jwtService = jwtService;
    }

    /* ── authed (shopkeeper) ── */

    @Transactional
    public String createLedgerShare(AuthUser authUser, UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
            .filter(c -> c.getBusinessId().equals(authUser.businessId()))
            .orElseThrow(() -> ApiException.notFound("Customer not found"));
        if (last4(customer.getPhone()) == null) {
            throw ApiException.unprocessable("NO_PARTY_PHONE",
                "Add this customer's phone number before sharing their ledger.");
        }
        ShareLink link = shareLinkRepository
            .findFirstByBusinessIdAndTypeAndCustomerIdAndRevokedFalse(
                authUser.businessId(), ShareType.LEDGER, customer.getId())
            .orElseGet(() -> {
                ShareLink l = new ShareLink();
                l.setToken(newToken());
                l.setBusinessId(authUser.businessId());
                l.setType(ShareType.LEDGER);
                l.setCustomerId(customer.getId());
                l.setCreatedBy(authUser.id());
                return shareLinkRepository.save(l);
            });
        return link.getToken();
    }

    @Transactional
    public String createBillShare(AuthUser authUser, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .filter(i -> i.getBusinessId().equals(authUser.businessId()))
            .orElseThrow(() -> ApiException.notFound("Bill not found"));
        if (last4(invoice.getPartyPhone()) == null) {
            throw ApiException.unprocessable("NO_PARTY_PHONE",
                "Add the customer's phone to the bill before sharing it.");
        }
        ShareLink link = shareLinkRepository
            .findFirstByBusinessIdAndTypeAndInvoiceIdAndRevokedFalse(
                authUser.businessId(), ShareType.BILL, invoice.getId())
            .orElseGet(() -> {
                ShareLink l = new ShareLink();
                l.setToken(newToken());
                l.setBusinessId(authUser.businessId());
                l.setType(ShareType.BILL);
                l.setInvoiceId(invoice.getId());
                l.setCreatedBy(authUser.id());
                return shareLinkRepository.save(l);
            });
        return link.getToken();
    }

    @Transactional
    public void revoke(AuthUser authUser, String token) {
        ShareLink link = shareLinkRepository.findByToken(token)
            .filter(l -> l.getBusinessId().equals(authUser.businessId()))
            .orElseThrow(() -> ApiException.notFound("Link not found"));
        link.setRevoked(true);
        shareLinkRepository.save(link);
    }

    /* ── public (verified customer) ── */

    @Transactional(readOnly = true)
    public ShareMetaResponse meta(String token) {
        ShareLink link = activeLink(token);
        boolean locked = link.getLockedUntil() != null && link.getLockedUntil().isAfter(Instant.now());
        return new ShareMetaResponse(shopName(link.getBusinessId()), link.getType(), locked);
    }

    /** Verify the phone last-4; returns a short-lived view token, or throws on wrong/locked. */
    @Transactional
    public String verify(String token, String last4) {
        ShareLink link = activeLink(token);
        Instant now = Instant.now();
        if (link.getLockedUntil() != null && link.getLockedUntil().isAfter(now)) {
            throw ApiException.unprocessable("SHARE_LOCKED", "Too many attempts. Please try again later.");
        }
        String expected = partyLast4(link);
        if (expected != null && expected.equals(last4)) {
            link.setFailedAttempts(0);
            link.setLockedUntil(null);
            shareLinkRepository.save(link);
            return jwtService.createShareViewToken(token);
        }
        int attempts = link.getFailedAttempts() + 1;
        link.setFailedAttempts(attempts);
        if (attempts >= 20) {
            link.setLockedUntil(now.plus(24, ChronoUnit.HOURS));
        } else if (attempts % 5 == 0) {
            link.setLockedUntil(now.plus(15, ChronoUnit.MINUTES));
        }
        shareLinkRepository.save(link);
        throw ApiException.badRequest("WRONG_LAST4", "Incorrect. Please check the last 4 digits.");
    }

    @Transactional(readOnly = true)
    public ShareLedgerResponse ledger(String token, String viewToken) {
        ShareLink link = requireVerified(token, viewToken, ShareType.LEDGER);
        Customer customer = customerRepository.findById(link.getCustomerId())
            .filter(c -> c.getBusinessId().equals(link.getBusinessId()))
            .orElseThrow(() -> ApiException.notFound("Not found"));
        PageResponse<EntryResponse> page = ledgerService.ledgerForCustomer(customer, 0, LEDGER_WINDOW);
        return new ShareLedgerResponse(shopName(link.getBusinessId()), customer.getName(),
            customer.getCurrentBalance(), customer.getOldestDueDate(), page.items());
    }

    @Transactional(readOnly = true)
    public byte[] billPdf(String token, String viewToken) {
        ShareLink link = requireVerified(token, viewToken, ShareType.BILL);
        return invoiceService.pdfForBusiness(link.getBusinessId(), link.getInvoiceId());
    }

    /* ── helpers ── */

    private ShareLink activeLink(String token) {
        return shareLinkRepository.findByToken(token)
            .filter(l -> !l.isRevoked())
            .orElseThrow(() -> ApiException.notFound("This link is no longer available."));
    }

    private ShareLink requireVerified(String token, String viewToken, ShareType expectedType) {
        ShareLink link = activeLink(token);
        if (link.getType() != expectedType) {
            throw ApiException.notFound("Not found");
        }
        try {
            Claims claims = jwtService.parse(viewToken);
            boolean ok = JwtService.TOKEN_TYPE_SHARE_VIEW.equals(claims.get("typ"))
                && token.equals(claims.getSubject());
            if (!ok) {
                throw ApiException.unauthorized("Please verify your identity first.");
            }
        } catch (JwtException e) {
            throw ApiException.unauthorized("Please verify your identity first.");
        }
        return link;
    }

    private String partyLast4(ShareLink link) {
        String phone = link.getType() == ShareType.LEDGER
            ? customerRepository.findById(link.getCustomerId()).map(Customer::getPhone).orElse(null)
            : invoiceRepository.findById(link.getInvoiceId()).map(Invoice::getPartyPhone).orElse(null);
        return last4(phone);
    }

    private String shopName(UUID businessId) {
        return businessRepository.findById(businessId).map(Business::getName).orElse("Shop");
    }

    private String newToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LEN);
        for (int i = 0; i < TOKEN_LEN; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Last 4 digits of a phone, or null if it doesn't have 4 digits. */
    private static String last4(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : null;
    }
}
