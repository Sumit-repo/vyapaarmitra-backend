package com.vyapaarmitra.api.reminder;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.reminder.ReminderDtos.CreateReminderRequest;
import com.vyapaarmitra.api.reminder.ReminderDtos.ReminderResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
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
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return reminderService.list(authUser, customerId, branchId, page, size);
    }
}
