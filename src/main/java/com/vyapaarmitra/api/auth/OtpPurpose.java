package com.vyapaarmitra.api.auth;

/** What an email one-time code authorises. */
public enum OtpPurpose {
    /** Passwordless sign-in for an existing account. */
    LOGIN,
    /** Email-verified self-serve signup that provisions a new business. */
    SIGNUP
}
