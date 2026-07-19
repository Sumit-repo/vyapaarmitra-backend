package com.vyapaarmitra.api.reminder;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerRepository;
import com.vyapaarmitra.api.customer.CustomerService;
import com.vyapaarmitra.api.reminder.ReminderSettingsDtos.DueReminderItem;
import com.vyapaarmitra.api.reminder.ReminderSettingsDtos.PromptedRequest;
import com.vyapaarmitra.api.reminder.ReminderSettingsDtos.ReminderMessageResponse;
import com.vyapaarmitra.api.reminder.ReminderSettingsDtos.ReminderSettingsResponse;
import com.vyapaarmitra.api.reminder.ReminderSettingsDtos.SentRequest;
import com.vyapaarmitra.api.reminder.ReminderSettingsDtos.UpdateReminderSettingsRequest;
import com.vyapaarmitra.api.template.MessageTemplate;
import com.vyapaarmitra.api.template.MessageTemplateRepository;
import com.vyapaarmitra.api.template.TemplateRenderer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderSettingsService {

    private static final int DUE_LIST_MAX = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final CustomerReminderSettingsRepository settingsRepository;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final BranchAccessService branchAccessService;
    private final MessageTemplateRepository templateRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final AppTime appTime;

    public ReminderSettingsService(CustomerReminderSettingsRepository settingsRepository,
                                   CustomerService customerService,
                                   CustomerRepository customerRepository,
                                   BranchAccessService branchAccessService,
                                   MessageTemplateRepository templateRepository,
                                   ReminderLogRepository reminderLogRepository,
                                   AppTime appTime) {
        this.settingsRepository = settingsRepository;
        this.customerService = customerService;
        this.customerRepository = customerRepository;
        this.branchAccessService = branchAccessService;
        this.templateRepository = templateRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.appTime = appTime;
    }

    /** Returns settings for a customer, creating defaults on first access. */
    @Transactional
    public ReminderSettingsResponse getSettings(AuthUser authUser, UUID customerId) {
        Customer customer = customerService.loadAccessible(authUser, customerId);
        CustomerReminderSettings settings = settingsRepository.findById(customerId)
            .orElseGet(() -> createDefaults(customer));
        return ReminderSettingsResponse.from(settings);
    }

    @Transactional
    public ReminderSettingsResponse updateSettings(AuthUser authUser, UUID customerId,
                                                   UpdateReminderSettingsRequest request) {
        Customer customer = customerService.loadAccessible(authUser, customerId);
        CustomerReminderSettings settings = settingsRepository.findById(customerId)
            .orElseGet(() -> createDefaults(customer));

        if (request.smsReminderEnabled() != null) {
            settings.setSmsReminderEnabled(request.smsReminderEnabled());
        }
        if (request.preferredChannel() != null) {
            settings.setPreferredChannel(request.preferredChannel());
        }
        if (request.reminderTemplateId() != null) {
            // Validate the template belongs to the same business
            templateRepository.findById(request.reminderTemplateId())
                .filter(t -> t.getBusinessId().equals(authUser.businessId()))
                .orElseThrow(() -> ApiException.notFound("Template not found"));
            settings.setReminderTemplateId(request.reminderTemplateId());
        }
        if (request.nextReminderDueAt() != null) {
            settings.setNextReminderDueAt(request.nextReminderDueAt());
        }
        if (request.autoScheduleEnabled() != null) {
            settings.setAutoScheduleEnabled(request.autoScheduleEnabled());
        }
        if (request.reminderNotes() != null) {
            settings.setReminderNotes(request.reminderNotes());
        }

        return ReminderSettingsResponse.from(settingsRepository.save(settings));
    }

    /**
     * Renders the customer's reminder message for use as SMS body in the Android compose intent.
     * Uses the customer's configured template if set; falls back to the first enabled template
     * matching the given type/category.
     */
    @Transactional(readOnly = true)
    public ReminderMessageResponse getReminderMessage(AuthUser authUser, UUID customerId,
                                                      String type) {
        Customer customer = customerService.loadAccessible(authUser, customerId);
        if (customer.getPhone() == null || customer.getPhone().isBlank()) {
            throw ApiException.unprocessable("NO_PHONE", "Customer has no phone number on file");
        }

        CustomerReminderSettings settings = settingsRepository.findById(customerId).orElse(null);
        MessageTemplate template = resolveTemplate(authUser, settings, type);

        Map<String, String> variables = buildReminderVariables(customer);
        TemplateRenderer.RenderResult result = TemplateRenderer.render(template.getBody(), variables);

        return new ReminderMessageResponse(
            customerId,
            customer.getPhone(),
            result.text(),
            template.getId(),
            settings != null ? settings.getPreferredChannel() : "SMS"
        );
    }

    /** Returns customers with SMS enabled that need a reminder prompt today. */
    @Transactional(readOnly = true)
    public List<DueReminderItem> getDueReminders(AuthUser authUser, UUID branchId) {
        Set<UUID> branchIds = branchAccessService.scope(authUser, branchId);
        Instant now = Instant.now();
        LocalDate today = appTime.today();

        List<CustomerReminderSettings> dueSettings = settingsRepository.findDue(
            authUser.businessId(), branchIds, now, today,
            PageRequest.of(0, DUE_LIST_MAX));

        List<UUID> customerIds = dueSettings.stream()
            .map(CustomerReminderSettings::getCustomerId)
            .toList();
        Map<UUID, Customer> customerMap = customerRepository.findAllById(customerIds).stream()
            .collect(Collectors.toMap(Customer::getId, c -> c));

        return dueSettings.stream()
            .map(s -> {
                Customer c = customerMap.get(s.getCustomerId());
                if (c == null) return null;
                int overdueDays = c.getOldestDueDate() != null
                    ? (int) Math.max(0, ChronoUnit.DAYS.between(c.getOldestDueDate(), today))
                    : 0;
                return new DueReminderItem(
                    c.getId(), c.getName(), c.getPhone(), c.getCurrentBalance(),
                    c.getOldestDueDate(), overdueDays, s.getPreferredChannel(),
                    s.getNextReminderDueAt(), s.getLastReminderSentAt()
                );
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /** Records that the merchant was shown a reminder prompt for this customer. */
    @Transactional
    public void markPrompted(AuthUser authUser, UUID customerId, PromptedRequest request) {
        Customer customer = customerService.loadAccessible(authUser, customerId);
        CustomerReminderSettings settings = settingsRepository.findById(customerId)
            .orElseGet(() -> createDefaults(customer));
        settings.setLastReminderPromptedAt(Instant.now());
        if (request != null && request.nextReminderDueAt() != null) {
            settings.setNextReminderDueAt(request.nextReminderDueAt());
        }
        settingsRepository.save(settings);
    }

    /**
     * Records that the merchant opened the SMS composer (the OS handoff happened).
     * Updates reminder metadata and appends a reminder_log entry.
     */
    @Transactional
    public void markSent(AuthUser authUser, UUID customerId, SentRequest request) {
        Customer customer = customerService.loadAccessible(authUser, customerId);
        CustomerReminderSettings settings = settingsRepository.findById(customerId)
            .orElseGet(() -> createDefaults(customer));

        Instant now = Instant.now();
        settings.setLastReminderSentAt(now);
        if (request != null && request.type() != null) {
            settings.setLastReminderType(request.type());
        }
        // Reset the schedule — merchant just sent, next prompt is merchant's call
        settings.setNextReminderDueAt(null);
        settingsRepository.save(settings);

        ReminderLog log = new ReminderLog();
        log.setBusinessId(customer.getBusinessId());
        log.setBranchId(customer.getBranchId());
        log.setCustomerId(customerId);
        log.setChannel(settings.getPreferredChannel());
        log.setOutcome(ReminderOutcome.REMINDER_SENT);
        log.setCreatedBy(authUser.id());
        if (request != null) {
            log.setTemplateId(request.templateId());
            log.setNote(request.note());
        }
        reminderLogRepository.save(log);
    }

    private CustomerReminderSettings createDefaults(Customer customer) {
        CustomerReminderSettings s = new CustomerReminderSettings();
        s.setCustomerId(customer.getId());
        s.setBusinessId(customer.getBusinessId());
        s.setBranchId(customer.getBranchId());
        return settingsRepository.save(s);
    }

    private MessageTemplate resolveTemplate(AuthUser authUser,
                                            CustomerReminderSettings settings, String type) {
        if (settings != null && settings.getReminderTemplateId() != null) {
            return templateRepository.findById(settings.getReminderTemplateId())
                .filter(t -> t.getBusinessId().equals(authUser.businessId()) && t.isEnabled())
                .orElseThrow(() -> ApiException.unprocessable("TEMPLATE_DISABLED",
                    "Customer's configured reminder template is disabled or not found"));
        }
        String category = (type != null && !type.isBlank()) ? type : "payment_due";
        return templateRepository.findFirstByBusinessIdAndCategoryIgnoreCaseAndEnabledTrue(
                authUser.businessId(), category)
            .orElseThrow(() -> ApiException.unprocessable("NO_TEMPLATE",
                "No enabled template found for type: " + category));
    }

    private Map<String, String> buildReminderVariables(Customer customer) {
        Map<String, String> vars = new HashMap<>();
        vars.put("customer_name", customer.getName());
        vars.put("amount_due", "₹" + customer.getCurrentBalance().stripTrailingZeros().toPlainString());
        LocalDate dueDate = customer.getOldestDueDate();
        if (dueDate != null) {
            vars.put("due_date", DATE_FORMAT.format(dueDate));
            long days = Math.max(0, ChronoUnit.DAYS.between(dueDate, appTime.today()));
            vars.put("overdue_days", String.valueOf(days));
        }
        return vars;
    }
}
