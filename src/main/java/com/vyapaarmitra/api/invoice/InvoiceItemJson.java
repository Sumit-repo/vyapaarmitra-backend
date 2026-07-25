package com.vyapaarmitra.api.invoice;

import java.math.BigDecimal;

/**
 * A single bill line, persisted inline in the invoice's {@code items} jsonb
 * column. {@code hsn} and {@code taxRate} apply to pakka (GST) bills only;
 * {@code lineAmount} is the computed qty × rate stored for read-time convenience.
 */
public record InvoiceItemJson(
    String name,
    BigDecimal qty,
    String unit,
    BigDecimal rate,
    String hsn,
    BigDecimal taxRate,
    BigDecimal lineAmount
) {
}
