package com.vyapaarmitra.api.subscription;

/**
 * Lifecycle of a business's subscription. This is the <em>stored</em> status; the
 * effective plan (which applies trial and grace windows) is derived in PlanService.
 */
public enum SubscriptionStatus {
    /** In the 14-day Pro trial, no paid plan yet. */
    TRIALING,
    /** A verified payment is in effect. */
    ACTIVE,
    /** A charge failed and we're in the dunning grace window. */
    PAST_DUE,
    /** Cancelled; access remains until current_period_end, then EXPIRED. */
    CANCELLED,
    /** Trial or paid period has fully lapsed — drops to FREE entitlements. */
    EXPIRED
}
