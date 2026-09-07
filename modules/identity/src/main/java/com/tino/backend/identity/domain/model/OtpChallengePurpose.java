package com.tino.backend.identity.domain.model;

/** Declares why a public OTP challenge is being requested. */
public enum OtpChallengePurpose {
    ACCOUNT_SIGN_UP,
    EXISTING_BUSINESS_LOGIN,
    /** Only the authenticated Business phone-change endpoint may use this purpose. */
    PHONE_CHANGE
}
