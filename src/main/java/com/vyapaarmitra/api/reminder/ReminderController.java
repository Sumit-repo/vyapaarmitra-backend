package com.vyapaarmitra.api.reminder;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.reminder.ReminderDtos.CreateReminderRequest;
import com.vyapaarmitra.api.reminder.ReminderDtos.ReminderResponse;
import com.vyapaarmitra.api.reminder.ReminderSettingsDtos.DueReminderItem;
import com.vyapaarmitra.api.reminder.ReminderSettingsDtos.PromptedRequest;
import com.vyapaarmitra.api.reminder.ReminderSettingsDtos.SentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    private final ReminderService reminderService;
    private final ReminderSettingsService reminderSettingsService;

    public ReminderController(ReminderService reminderService,
                              ReminderSettingsService reminderSettingsService) {
        this.reminderService = reminderService;
        this.reminderSettingsService = reminderSettingsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse create(@AuthenticationPrincipal AuthUser authUser,
                                   @Valid @RequestBody CreateReminderRequest request) {
        return reminderService.create(authUser, request);
    }

    @GetMapping
    public PageResponse<ReminderResponse> list(@AuthenticationPrincipal AuthUser authUser,
                                               @RequestParam(required = false) UUID customerId,
                                               @RequestParam(required = false) UUID branchId,
                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reminderService.list(authUser, customerId, branchId, page, size);
    }

    /** Reminder center: customers that need a prompt today (SMS-enabled + overdue/scheduled). */
    @GetMapping("/due")
    public List<DueReminderItem> due(@AuthenticationPrincipal AuthUser authUser,
                                     @RequestParam(required = false) UUID branchId) {
        return reminderSettingsService.getDueReminders(authUser, branchId);
    }

    /** Merchant was shown a reminder prompt for this customer. Optionally reschedule next prompt. */
    @PostMapping("/customers/{customerId}/prompted")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void prompted(@AuthenticationPrincipal AuthUser authUser,
                         @PathVariable UUID customerId,
                         @RequestBody(required = false) PromptedRequest request) {
        reminderSettingsService.markPrompted(authUser, customerId, request);
    }

    /** Merchant opened the Android SMS composer — the OS handoff happened. Logs the reminder. */
    @PostMapping("/customers/{customerId}/sent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sent(@AuthenticationPrincipal AuthUser authUser,
                     @PathVariable UUID customerId,
                     @Valid @RequestBody(required = false) SentRequest request) {
        reminderSettingsService.markSent(authUser, customerId, request);
    }
}
