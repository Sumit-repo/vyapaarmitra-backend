package com.vyapaarmitra.api.reminder;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.common.PageResponse;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerService;
import com.vyapaarmitra.api.reminder.ReminderDtos.CreateReminderRequest;
import com.vyapaarmitra.api.reminder.ReminderDtos.ReminderResponse;
import com.vyapaarmitra.api.user.UserDirectory;
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
public class ReminderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReminderLogRepository reminderLogRepository;
    private final CustomerService customerService;
    private final BranchAccessService branchAccessService;
    private final UserDirectory userDirectory;

    public ReminderService(ReminderLogRepository reminderLogRepository,
                           CustomerService customerService,
                           BranchAccessService branchAccessService,
                           UserDirectory userDirectory) {
        this.reminderLogRepository = reminderLogRepository;
        this.customerService = customerService;
        this.branchAccessService = branchAccessService;
        this.userDirectory = userDirectory;
    }

    @Transactional
    public ReminderResponse create(AuthUser authUser, CreateReminderRequest request) {
        Customer customer = customerService.loadAccessible(authUser, request.customerId());
        ReminderLog log = new ReminderLog();
        log.setBusinessId(customer.getBusinessId());
        log.setBranchId(customer.getBranchId());
        log.setCustomerId(customer.getId());
        log.setTemplateId(request.templateId());
        log.setChannel(request.channel());
        log.setOutcome(request.outcome());
        log.setPromisedDate(request.promisedDate());
        log.setNote(request.note());
        log.setCreatedBy(authUser.id());
        ReminderLog saved = reminderLogRepository.save(log);
        return ReminderResponse.from(saved,
            userDirectory.name(saved.getBusinessId(), saved.getCreatedBy()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReminderResponse> list(AuthUser authUser, UUID customerId, UUID branchId,
                                               int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page),
            Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        Page<ReminderLog> logs;
        if (customerId != null) {
            customerService.loadAccessible(authUser, customerId);
            logs = reminderLogRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
        } else {
            Set<UUID> branchIds = branchAccessService.scope(authUser, branchId);
            logs = reminderLogRepository.findByBranchIdInOrderByCreatedAtDesc(branchIds, pageable);
        }
        List<ReminderLog> content = logs.getContent();
        Map<UUID, String> names = userDirectory.namesByBusiness(authUser.businessId(),
            content.stream().map(ReminderLog::getCreatedBy).toList());
        return PageResponse.of(logs.map(r -> ReminderResponse.from(r, names.get(r.getCreatedBy()))));
    }
}
