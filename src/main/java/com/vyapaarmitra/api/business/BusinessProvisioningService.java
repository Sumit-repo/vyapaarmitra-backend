package com.vyapaarmitra.api.business;

import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.membership.MembershipRepository;
import com.vyapaarmitra.api.subscription.PlanTier;
import com.vyapaarmitra.api.subscription.Subscription;
import com.vyapaarmitra.api.subscription.SubscriptionRepository;
import com.vyapaarmitra.api.subscription.SubscriptionStatus;
import com.vyapaarmitra.api.template.MessageTemplate;
import com.vyapaarmitra.api.template.MessageTemplateRepository;
import com.vyapaarmitra.api.template.TemplateChannel;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final MembershipRepository membershipRepository;
    private final MessageTemplateRepository templateRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    private static final int TRIAL_DAYS = 14;

    public BusinessProvisioningService(BusinessRepository businessRepository,
                                       BranchRepository branchRepository,
                                       UserRepository userRepository,
                                       MembershipRepository membershipRepository,
                                       MessageTemplateRepository templateRepository,
                                       SubscriptionRepository subscriptionRepository,
                                       PasswordEncoder passwordEncoder) {
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.templateRepository = templateRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates business + first branch + password owner + starter templates. Returns the owner. */
    @Transactional
    public User provision(String businessName, String branchName, String ownerName,
                          String email, String phone, String rawPassword,
                          boolean defaulterNetworkConsent) {
        return provisionInternal(businessName, branchName, ownerName, email, phone,
            passwordEncoder.encode(rawPassword), null, false, defaulterNetworkConsent);
    }

    /**
     * Creates a business owned by a Google-verified account (no password, email
     * already verified, Google subject linked). Phone is collected later. Returns the owner.
     * OAuth signups can't tick the network-consent box in-flow, so it defaults off.
     */
    @Transactional
    public User provisionOAuth(String businessName, String branchName, String ownerName,
                               String email, String googleSub) {
        return provisionInternal(businessName, branchName, ownerName, email, null,
            null, googleSub, true, false);
    }

    private User provisionInternal(String businessName, String branchName, String ownerName,
                                   String email, String phone, String passwordHash,
                                   String googleSub, boolean emailVerified,
                                   boolean defaulterNetworkConsent) {
        User owner = new User();
        // The source of truth for access is the OWNER membership created below; the
        // user row is just the identity (legacy business_id/role were dropped in V10).
        owner.setEmail(email.toLowerCase());
        owner.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        owner.setPasswordHash(passwordHash);
        owner.setFullName(ownerName);
        owner.setGoogleSub(googleSub);
        owner.setEmailVerified(emailVerified);
        owner.setDefaulterNetworkConsent(defaulterNetworkConsent);
        userRepository.save(owner);

        provisionBusinessFor(owner, businessName, branchName);
        return owner;
    }

    /**
     * Stands up another business owned by an EXISTING identity: business + first branch +
     * OWNER membership + starter templates + subscription. The subscription is a 14-day Pro
     * trial only if this person hasn't used their one trial yet — otherwise it starts FREE.
     * Returns the new business.
     */
    @Transactional
    public Business provisionAdditionalBusiness(User owner, String businessName, String branchName) {
        return provisionBusinessFor(owner, businessName, branchName);
    }

    private Business provisionBusinessFor(User owner, String businessName, String branchName) {
        Business business = new Business();
        business.setName(businessName);
        businessRepository.save(business);

        Branch branch = new Branch();
        branch.setBusinessId(business.getId());
        branch.setName(branchName);
        branchRepository.save(branch);

        Membership ownerMembership = new Membership();
        ownerMembership.setUserId(owner.getId());
        ownerMembership.setBusinessId(business.getId());
        ownerMembership.setRole(Role.OWNER);
        membershipRepository.save(ownerMembership);

        seedStarterTemplates(business);
        seedSubscription(business, owner);
        return business;
    }

    /**
     * First business a person creates gets the 14-day Pro trial (marking the identity's
     * one trial as used); every business after that starts FREE (status EXPIRED, no trial
     * window) so nobody farms trials by opening shops.
     */
    private void seedSubscription(Business business, User owner) {
        Subscription sub = new Subscription();
        sub.setBusinessId(business.getId());
        if (owner.isTrialUsed()) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setPlan(PlanTier.FREE);
            // trialEndsAt stays null → effectivePlan yields FREE.
        } else {
            sub.setTrialEndsAt(Instant.now().plus(TRIAL_DAYS, ChronoUnit.DAYS));
            owner.setTrialUsed(true);
            userRepository.save(owner);
        }
        subscriptionRepository.save(sub);
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
