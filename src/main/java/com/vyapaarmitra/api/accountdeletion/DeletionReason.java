package com.vyapaarmitra.api.accountdeletion;

/**
 * Why an owner chose to delete — captured (optionally) so the shop owner can reach out
 * during the 30-day grace and to mine churn reasons. Paradox of Choice: 4 concrete
 * reasons + OTHER (free text). Mirrored in each client's i18n. See docs/account-deletion.md.
 */
public enum DeletionReason {
    /** It costs too much / I don't want to pay. */
    TOO_EXPENSIVE,
    /** I'm not using it / shop closed. */
    NOT_USING,
    /** Missing features or something's not working. */
    MISSING_OR_BROKEN,
    /** Privacy / data concern. */
    PRIVACY,
    /** Anything else — pairs with optional free-text feedback. */
    OTHER
}
