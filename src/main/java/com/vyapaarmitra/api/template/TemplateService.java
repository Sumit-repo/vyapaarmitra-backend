package com.vyapaarmitra.api.template;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.Branch;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.business.BranchRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.config.AppProperties;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerService;
import com.vyapaarmitra.api.share.ShareService;
import com.vyapaarmitra.api.template.TemplateDtos.CreateTemplateRequest;
import com.vyapaarmitra.api.template.TemplateDtos.RenderResponse;
import com.vyapaarmitra.api.template.TemplateDtos.TemplateResponse;
import com.vyapaarmitra.api.template.TemplateDtos.UpdateTemplateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
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

    private final MessageTemplateRepository templateRepository;
    private final BranchRepository branchRepository;
    private final BranchAccessService branchAccessService;
    private final CustomerService customerService;
    private final ShareService shareService;
    private final AppProperties appProperties;
    private final AppTime appTime;

    public TemplateService(MessageTemplateRepository templateRepository,
                           BranchRepository branchRepository,
                           BranchAccessService branchAccessService,
                           CustomerService customerService,
                           ShareService shareService,
                           AppProperties appProperties,
                           AppTime appTime) {
        this.templateRepository = templateRepository;
        this.branchRepository = branchRepository;
        this.branchAccessService = branchAccessService;
        this.customerService = customerService;
        this.shareService = shareService;
        this.appProperties = appProperties;
        this.appTime = appTime;
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
    public RenderResponse render(AuthUser authUser, UUID templateId, UUID customerId) {
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

        Map<String, String> variables = buildVariables(customer);
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

    private Map<String, String> buildVariables(Customer customer) {
        Map<String, String> variables = new HashMap<>();
        variables.put("customer_name", customer.getName());
        variables.put("amount_due", formatAmount(customer.getCurrentBalance()));
        LocalDate dueDate = customer.getOldestDueDate();
        if (dueDate != null) {
            variables.put("due_date", DATE_FORMAT.format(dueDate));
            long overdueDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, appTime.today()));
            variables.put("overdue_days", String.valueOf(overdueDays));
        }
        branchRepository.findById(customer.getBranchId())
            .map(Branch::getName)
            .ifPresent(name -> variables.put("branch_name", name));
        return variables;
    }

    private String formatAmount(BigDecimal amount) {
        return "₹" + amount.stripTrailingZeros().toPlainString();
    }

    private MessageTemplate loadOwned(AuthUser authUser, UUID templateId) {
        return templateRepository.findById(templateId)
            .filter(t -> t.getBusinessId().equals(authUser.businessId()))
            .orElseThrow(() -> ApiException.notFound("Template not found"));
    }
}
