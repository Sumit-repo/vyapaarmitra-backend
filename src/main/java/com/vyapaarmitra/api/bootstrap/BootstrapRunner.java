package com.vyapaarmitra.api.bootstrap;

import com.vyapaarmitra.api.business.Branch;
import com.vyapaarmitra.api.business.BranchRepository;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.config.AppProperties;
import com.vyapaarmitra.api.template.MessageTemplate;
import com.vyapaarmitra.api.template.MessageTemplateRepository;
import com.vyapaarmitra.api.template.TemplateChannel;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-boot seeding for the single-shop pilot: creates the business, its main
 * branch, the owner account, and a starter set of reminder templates. Runs only
 * when the users table is empty and BOOTSTRAP_OWNER_EMAIL is configured, so it
 * is safe on every Cloud Run cold start.
 */
@Slf4j
@Component
public class BootstrapRunner implements ApplicationRunner {

    private final AppProperties props;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final MessageTemplateRepository templateRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapRunner(AppProperties props, UserRepository userRepository,
                           BusinessRepository businessRepository,
                           BranchRepository branchRepository,
                           MessageTemplateRepository templateRepository,
                           PasswordEncoder passwordEncoder) {
        this.props = props;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.templateRepository = templateRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap bootstrap = props.bootstrap();
        if (bootstrap.ownerEmail() == null || bootstrap.ownerEmail().isBlank()) {
            return;
        }
        if (userRepository.count() > 0) {
            return;
        }
        if (bootstrap.ownerPassword() == null || bootstrap.ownerPassword().isBlank()) {
            log.warn("BOOTSTRAP_OWNER_EMAIL set but BOOTSTRAP_OWNER_PASSWORD missing; skipping bootstrap");
            return;
        }

        Business business = new Business();
        business.setName(bootstrap.businessName());
        businessRepository.save(business);

        Branch branch = new Branch();
        branch.setBusinessId(business.getId());
        branch.setName(bootstrap.branchName());
        branchRepository.save(branch);

        User owner = new User();
        owner.setBusinessId(business.getId());
        owner.setEmail(bootstrap.ownerEmail().toLowerCase());
        owner.setPasswordHash(passwordEncoder.encode(bootstrap.ownerPassword()));
        owner.setFullName(bootstrap.ownerName());
        owner.setRole(Role.OWNER);
        userRepository.save(owner);

        seedTemplate(business, TemplateChannel.WHATSAPP, "soft_reminder", "Soft reminder",
            "Namaste {{customer_name}} ji, {{branch_name}} se. Aapka {{amount_due}} baaki hai. "
                + "Jab suvidha ho, kripya settle kar dein. Dhanyavaad!");
        seedTemplate(business, TemplateChannel.WHATSAPP, "firm_reminder", "Firm reminder",
            "{{customer_name}} ji, {{branch_name}} se reminder: {{amount_due}} {{overdue_days}} din "
                + "se pending hai (due date {{due_date}}). Kripya jaldi payment karein.");
        seedTemplate(business, TemplateChannel.SMS, "monthly_settlement", "Monthly settlement",
            "{{customer_name}} ji, is mahine ka hisaab: {{amount_due}} due hai. "
                + "{{branch_name}}. Kripya settle karein.");

        log.info("Bootstrap complete: business '{}' with owner '{}'", business.getName(),
            owner.getEmail());
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
