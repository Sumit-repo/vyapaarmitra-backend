package com.vyapaarmitra.api.template;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.Branch;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.business.BranchRepository;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.config.AppProperties;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerService;
import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import com.vyapaarmitra.api.share.ShareService;
import com.vyapaarmitra.api.template.TemplateDtos.CreateTemplateRequest;
import com.vyapaarmitra.api.template.TemplateDtos.RenderResponse;
import com.vyapaarmitra.api.template.TemplateDtos.TemplateResponse;
import com.vyapaarmitra.api.template.TemplateDtos.UpdateTemplateRequest;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    /**
     * The seeding-default branch name (BusinessController.DEFAULT_BRANCH_NAME — kept local
     * to avoid importing a controller constant into the template package). A branch with
     * this name is the auto-created one, not something the owner typed.
     */
    private static final String DEFAULT_BRANCH_NAME = "Main Branch";

    private final MessageTemplateRepository templateRepository;
    private final BranchRepository branchRepository;
    private final BusinessRepository businessRepository;
    private final BranchAccessService branchAccessService;
    private final CustomerService customerService;
    private final ShareService shareService;
    private final AppProperties appProperties;
    private final AppTime appTime;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TemplateService(MessageTemplateRepository templateRepository,
                           BranchRepository branchRepository,
                           BusinessRepository businessRepository,
                           BranchAccessService branchAccessService,
                           CustomerService customerService,
                           ShareService shareService,
                           AppProperties appProperties,
                           AppTime appTime,
                           LedgerEntryRepository ledgerEntryRepository) {
        this.templateRepository = templateRepository;
        this.branchRepository = branchRepository;
        this.businessRepository = businessRepository;
        this.branchAccessService = branchAccessService;
        this.customerService = customerService;
        this.shareService = shareService;
        this.appProperties = appProperties;
        this.appTime = appTime;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(AuthUser authUser, UUID branchId) {
        List<MessageTemplate> templates;
        if (branchId != null) {
            branchAccessService.assertBranchAccess(authUser, branchId);
            templates = templateRepository.findForBranch(authUser.businessId(), branchId);
        } else {
            templates = templateRepository
                .findByBusinessIdOrderByCategoryAscNameAsc(authUser.businessId());
        }
        return templates.stream().map(TemplateResponse::from).toList();
    }

    @Transactional
    public TemplateResponse create(AuthUser authUser, CreateTemplateRequest request) {
        if (request.branchId() != null) {
            branchAccessService.assertBranchAccess(authUser, request.branchId());
        }
        MessageTemplate template = new MessageTemplate();
        template.setBusinessId(authUser.businessId());
        template.setBranchId(request.branchId());
        template.setCategory(request.category().trim());
        template.setName(request.name().trim());
        template.setBody(request.body());
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse update(AuthUser authUser, UUID templateId,
                                   UpdateTemplateRequest request) {
        MessageTemplate template = loadOwned(authUser, templateId);
        if (request.category() != null) {
            template.setCategory(request.category().trim());
        }
        if (request.name() != null) {
            template.setName(request.name().trim());
        }
        if (request.body() != null) {
            template.setBody(request.body());
        }
        if (request.enabled() != null) {
            template.setEnabled(request.enabled());
        }
        return TemplateResponse.from(templateRepository.save(template));
    }

    // Not read-only: appending the khata link get-or-creates the customer's share link.
    @Transactional
    public RenderResponse render(AuthUser authUser, UUID templateId, UUID customerId,
                                 LocalDate startDate, LocalDate endDate) {
        MessageTemplate template = loadOwned(authUser, templateId);
        if (!template.isEnabled()) {
            throw ApiException.unprocessable("TEMPLATE_DISABLED", "This template is disabled");
        }
        Customer customer = customerService.loadAccessible(authUser, customerId);
        if (template.getBranchId() != null
            && !template.getBranchId().equals(customer.getBranchId())) {
            throw ApiException.unprocessable("TEMPLATE_BRANCH_MISMATCH",
                "Template belongs to a different branch");
        }
        if ((startDate == null) != (endDate == null)) {
            throw ApiException.unprocessable("INVALID_DATE_RANGE",
                "Provide both startDate and endDate, or neither");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw ApiException.unprocessable("INVALID_DATE_RANGE", "startDate is after endDate");
        }

        Map<String, String> variables = buildVariables(customer, startDate, endDate);
        TemplateRenderer.RenderResult result = TemplateRenderer.render(template.getBody(), variables);
        if (!result.ok()) {
            throw ApiException.unprocessable("TEMPLATE_MISSING_VARIABLES",
                "Missing values for: " + String.join(", ", result.missingVariables()));
        }
        return new RenderResponse(template.getId(), appendKhataLink(authUser, customer, result.text()));
    }

    /**
     * Append the customer's public khata link so every reminder carries it. Only when the party
     * has a usable phone (the viewer's second factor is its last 4 digits) — we pre-check rather
     * than catch, because createLedgerShare joins this transaction and its throw would mark it
     * rollback-only, failing the whole render even if caught.
     */
    private String appendKhataLink(AuthUser authUser, Customer customer, String text) {
        if (!hasLast4(customer.getPhone())) {
            log.debug("Skipping khata link for customer {}: no usable phone", customer.getId());
            return text;
        }
        String token = shareService.createLedgerShare(authUser, customer.getId());
        String url = appProperties.webUrl().replaceAll("/+$", "") + "/s/" + token;
        return text + "\n\nअपना हिसाब देखें / View your khata:\n" + url;
    }

    /** Mirrors ShareService: a phone is usable as the viewer's second factor only with ≥4 digits. */
    private static boolean hasLast4(String phone) {
        return (phone == null ? "" : phone.replaceAll("\\D", "")).length() >= 4;
    }

    private Map<String, String> buildVariables(Customer customer, LocalDate startDate,
                                               LocalDate endDate) {
        Map<String, String> variables = new HashMap<>();
        variables.put("customer_name", customer.getName());
        variables.put("amount_due", formatAmount(customer.getCurrentBalance()));
        LocalDate dueDate = customer.getOldestDueDate();
        if (dueDate != null) {
            variables.put("due_date", DATE_FORMAT.format(dueDate));
            long overdueDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, appTime.today()));
            variables.put("overdue_days", String.valueOf(overdueDays));
        }
        if (startDate != null) {
            putWindowVariables(variables, customer, startDate, endDate);
        }
        // The shop's own name — the default signature for customer-facing reminders.
        String businessName = businessRepository.findById(customer.getBusinessId())
            .map(Business::getName)
            .orElse(null);
        if (businessName != null) {
            variables.put("business_name", businessName);
        }
        // branch_name: for a shop that never renamed its auto-created branch, "Main Branch"
        // reads like a bank notice — sign with the shop name instead. Older/custom templates
        // that still say {{branch_name}} therefore render the same signature the client
        // preview shows. A branch the owner actually named keeps its real name.
        branchRepository.findById(customer.getBranchId())
            .map(Branch::getName)
            .filter(name -> !DEFAULT_BRANCH_NAME.equals(name))
            .ifPresent(name -> variables.put("branch_name", name));
        if (!variables.containsKey("branch_name") && businessName != null) {
            variables.put("branch_name", businessName);
        }
        return variables;
    }

    /**
     * Settlement-window tokens for the monthly-reminder flow (render guarantees both dates).
     * The window is inclusive of both boundary days: half-open [start 00:00, end+1 00:00) in
     * the business timezone. window_due is the running balance at window close — today's
     * balance minus everything recorded after — so a past window shows what was owed then,
     * not now. Deliberately only on this path: ReminderSettingsService's automatic message
     * has no date context, so a window-bearing template used there 422s with the missing
     * variables named, which reads as the error it is.
     */
    private void putWindowVariables(Map<String, String> variables, Customer customer,
                                    LocalDate startDate, LocalDate endDate) {
        Instant from = appTime.startOfDay(startDate);
        Instant to = appTime.startOfDay(endDate.plusDays(1));
        variables.put("window_start", DATE_FORMAT.format(startDate));
        variables.put("window_end", DATE_FORMAT.format(endDate));
        variables.put("window_credit", formatAmount(ledgerEntryRepository
            .sumByCustomerAndTypeBetween(customer.getId(), EntryType.CREDIT, from, to)));
        variables.put("window_payment", formatAmount(ledgerEntryRepository
            .sumByCustomerAndTypeBetween(customer.getId(), EntryType.PAYMENT, from, to)));
        variables.put("window_due", formatAmount(
            customer.getCurrentBalance().subtract(
                ledgerEntryRepository.signedSumAfter(customer.getId(), to))));
    }

    private String formatAmount(BigDecimal amount) {
        // Indian digit grouping ("₹18,000", "₹1,31,224") — must match the client-side
        // rupees() formatting, or the delivered message reads differently from the preview.
        return "₹" + NumberFormat.getNumberInstance(new Locale("en", "IN")).format(amount);
    }

    private MessageTemplate loadOwned(AuthUser authUser, UUID templateId) {
        return templateRepository.findById(templateId)
            .filter(t -> t.getBusinessId().equals(authUser.businessId()))
            .orElseThrow(() -> ApiException.notFound("Template not found"));
    }
}
