package com.vyapaarmitra.api.template;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.Branch;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.business.BranchRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final MessageTemplateRepository templateRepository;
    private final BranchRepository branchRepository;
    private final BranchAccessService branchAccessService;
    private final CustomerService customerService;
    private final AppTime appTime;

    public TemplateService(MessageTemplateRepository templateRepository,
                           BranchRepository branchRepository,
                           BranchAccessService branchAccessService,
                           CustomerService customerService,
                           AppTime appTime) {
        this.templateRepository = templateRepository;
        this.branchRepository = branchRepository;
        this.branchAccessService = branchAccessService;
        this.customerService = customerService;
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
        template.setChannel(request.channel());
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

    @Transactional(readOnly = true)
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
        return new RenderResponse(template.getId(), template.getChannel(), result.text());
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
