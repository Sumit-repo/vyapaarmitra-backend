package com.vyapaarmitra.api.bootstrap;

import com.vyapaarmitra.api.business.BusinessProvisioningService;
import com.vyapaarmitra.api.config.AppProperties;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-boot seeding for the single-shop pilot: provisions the business, its main
 * branch, the owner account, and starter reminder templates. Runs only when the
 * users table is empty and BOOTSTRAP_OWNER_EMAIL is configured, so it is safe on
 * every Cloud Run cold start.
 */
@Slf4j
@Component
public class BootstrapRunner implements ApplicationRunner {

    private final AppProperties props;
    private final UserRepository userRepository;
    private final BusinessProvisioningService provisioningService;

    public BootstrapRunner(AppProperties props, UserRepository userRepository,
                           BusinessProvisioningService provisioningService) {
        this.props = props;
        this.userRepository = userRepository;
        this.provisioningService = provisioningService;
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

        User owner = provisioningService.provision(bootstrap.businessName(), bootstrap.branchName(),
            bootstrap.ownerName(), bootstrap.ownerEmail(), null, bootstrap.ownerPassword(), false);

        log.info("Bootstrap complete: business '{}' with owner '{}'",
            bootstrap.businessName(), owner.getEmail());
    }
}
