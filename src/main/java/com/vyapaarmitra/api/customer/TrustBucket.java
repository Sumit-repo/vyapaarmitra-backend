package com.vyapaarmitra.api.customer;

public enum TrustBucket {
    GOOD,
    WATCH,
    RISKY,
    /** No credit history yet — observation, not a risk judgment. */
    NEW
}
