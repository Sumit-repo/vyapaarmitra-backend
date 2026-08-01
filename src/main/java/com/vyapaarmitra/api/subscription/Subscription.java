package com.vyapaarmitra.api.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A business's subscription record — the source of truth for billing state. The
 * stored {@code plan}/{@code status} are the base facts; PlanService derives the
 * effective plan (trial → PRO, grace windows) on read.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanTier plan = PlanTier.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.TRIALING;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period")
    private BillingPeriod billingPeriod;

    /**
     * The plan/period a checkout is trying to buy. Set at checkout and applied to
     * {@code plan}/{@code billingPeriod} only on a verified activated/charged webhook,
     * so an unpaid or cancelled checkout never grants the tier. Null when idle.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_plan")
    private PlanTier pendingPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_billing_period")
    private BillingPeriod pendingBillingPeriod;

    // Null when the business never had a trial (created by an identity that already used
    // its one trial). effectivePlan/view guard on null → FREE entitlements.
    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "grace_until")
    private Instant graceUntil;

    private String gateway;

    @Column(name = "gateway_sub_id", unique = true)
    private String gatewaySubId;

    @Column(name = "gateway_customer_id")
    private String gatewayCustomerId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
