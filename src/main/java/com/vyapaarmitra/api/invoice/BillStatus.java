package com.vyapaarmitra.api.invoice;

/** Payment state derived from grand total vs amount received. */
public enum BillStatus {
    PAID,
    PARTIAL,
    UNPAID
}
