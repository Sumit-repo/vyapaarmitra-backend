package com.vyapaarmitra.api.invoice;

/** How a bill was settled. CREDIT = added to the customer's khata (udhar). */
public enum PaymentMode {
    CASH,
    UPI,
    CREDIT
}
