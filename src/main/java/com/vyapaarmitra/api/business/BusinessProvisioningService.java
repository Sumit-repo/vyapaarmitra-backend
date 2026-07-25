package com.vyapaarmitra.api.business;

import com.vyapaarmitra.api.template.MessageTemplate;
import com.vyapaarmitra.api.template.MessageTemplateRepository;
import com.vyapaarmitra.api.template.TemplateChannel;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single source of truth for standing up a brand-new business: the business
 * record, its first branch, the OWNER account, and a starter set of reminder
 * templates. Used by both first-boot bootstrap and self-serve registration so
 * the two paths never drift.
 */
@Service
public class BusinessProvisioningService {

    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final MessageTemplateRepository templateRepository;
    private final PasswordEncoder passwordEncoder;

    public BusinessProvisioningService(BusinessRepository businessRepository,
                                       BranchRepository branchRepository,
                                       UserRepository userRepository,
                                       MessageTemplateRepository templateRepository,
                                       PasswordEncoder passwordEncoder) {
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.templateRepository = templateRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates business + first branch + owner + starter templates. Returns the owner. */
    @Transactional
    public User provision(String businessName, String branchName, String ownerName,
                          String email, String rawPassword) {
        Business business = new Business();
        business.setName(businessName);
        businessRepository.save(business);

        Branch branch = new Branch();
        branch.setBusinessId(business.getId());
        branch.setName(branchName);
        branchRepository.save(branch);

        User owner = new User();
        owner.setBusinessId(business.getId());
        owner.setEmail(email.toLowerCase());
        owner.setPasswordHash(passwordEncoder.encode(rawPassword));
        owner.setFullName(ownerName);
        owner.setRole(Role.OWNER);
        userRepository.save(owner);

        seedStarterTemplates(business);
        return owner;
    }

    private void seedStarterTemplates(Business business) {
        seedTemplate(business, TemplateChannel.WHATSAPP, "soft_reminder", "Soft reminder",
            "Namaste {{customer_name}} ji, {{branch_name}} se. Aapka {{amount_due}} baaki hai. "
                + "Jab suvidha ho, kripya settle kar dein. Dhanyavaad!");
        seedTemplate(business, TemplateChannel.WHATSAPP, "firm_reminder", "Firm reminder",
            "{{customer_name}} ji, {{branch_name}} se reminder: {{amount_due}} {{overdue_days}} din "
                + "se pending hai (due date {{due_date}}). Kripya jaldi payment karein.");
        seedTemplate(business, TemplateChannel.SMS, "monthly_settlement", "Monthly settlement",
            "{{customer_name}} ji, is mahine ka hisaab: {{amount_due}} due hai. "
                + "{{branch_name}}. Kripya settle karein.");
    }

    private void seedTemplate(Business business, TemplateChannel channel, String category,
                              String name, String body) {
        MessageTemplate template = new MessageTemplate();
        template.setBusinessId(business.getId());
        template.setChannel(channel);
        template.setCategory(category);
        template.setName(name);
        template.setBody(body);
        templateRepository.save(template);
    }
}
