package com.vyapaarmitra.api.defaulter;

/**
 * Lifecycle of a cross-business defaulter report. A phone is "flagged" on the network only
 * while an {@link #ACTIVE} report exists for it.
 */
public enum DefaulterStatus {
    /** Warning SMS sent by the merchant; 7-day grace running. Not yet visible on the network. */
    WARNING,
    /** Grace lapsed while still unpaid — the network Defaulter badge is live. */
    ACTIVE,
    /** Debt settled (pay-to-clear) or grace ended already-paid — no longer flagged. */
    CLEARED
}
