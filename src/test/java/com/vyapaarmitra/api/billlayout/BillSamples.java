package com.vyapaarmitra.api.billlayout;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vyapaarmitra.api.invoice.BillStatus;
import com.vyapaarmitra.api.invoice.BillType;
import com.vyapaarmitra.api.invoice.InvoiceDtos.InvoiceResponse;
import com.vyapaarmitra.api.invoice.InvoiceItemJson;
import com.vyapaarmitra.api.invoice.PaymentMode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fixed bill fixtures shared by the renderer tests. Deterministic (fixed ids, dates and
 * money) so the golden string test is stable and the preset previews are comparable.
 */
final class BillSamples {

    private BillSamples() {
    }

    static InvoiceItemJson item(String name, String hsn, String qty, String rate, String tax) {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal r = new BigDecimal(rate);
        return new InvoiceItemJson(name, q, "pcs", r, hsn, tax == null ? null : new BigDecimal(tax),
            q.multiply(r));
    }

    /** Two items, a discount, GST and a part payment — every branch of the builder fires. */
    static InvoiceResponse bill(BillType type) {
        boolean pakka = type == BillType.PAKKA;
        List<InvoiceItemJson> items = List.of(
            item("Cement Bag", pakka ? "2523" : null, "2", "400", pakka ? "18" : null),
            item("Steel Rod", pakka ? "7214" : null, "1", "250", pakka ? "18" : null));
        BigDecimal subtotal = new BigDecimal("1050");
        BigDecimal discount = new BigDecimal("50");
        BigDecimal taxTotal = pakka ? new BigDecimal("162") : BigDecimal.ZERO;
        BigDecimal cgst = pakka ? new BigDecimal("81") : BigDecimal.ZERO;
        BigDecimal sgst = pakka ? new BigDecimal("81") : BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        BigDecimal grandTotal = subtotal.subtract(discount).add(taxTotal);
        BigDecimal received = new BigDecimal("500");
        BigDecimal balanceDue = grandTotal.subtract(received);
        return new InvoiceResponse(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            type, pakka ? "INV-0042" : "EST-0042",
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            "Ramesh Kumar", "+91 90000 00000", "07BBBBB1111B1Z5",
            pakka ? "07AAAAA0000A1Z5" : null, pakka ? "Delhi" : null, false,
            items, discount, subtotal, taxTotal, cgst, sgst, igst,
            grandTotal, received, balanceDue,
            PaymentMode.UPI, BillStatus.PARTIAL, "Thank you, visit again!",
            null, Instant.parse("2026-09-03T04:30:00Z"),
            UUID.fromString("00000000-0000-0000-0000-000000000004"), "Sumit");
    }

    /** Sanity guard so a fixture edit can't silently desync from the goldens. */
    static void assertConsistent(InvoiceResponse bill) {
        assertTrue(bill.grandTotal().compareTo(bill.amountReceived()) > 0);
    }

    /** The same bill settled in full — for the "paid in full" / no-QR branches. */
    static InvoiceResponse paid(BillType type) {
        InvoiceResponse b = bill(type);
        return new InvoiceResponse(b.id(), b.branchId(), b.billType(), b.number(),
            b.partyCustomerId(), b.partyName(), b.partyPhone(), b.partyGstin(), b.sellerGstin(),
            b.placeOfSupply(), b.interState(), b.items(), b.discount(), b.subtotal(),
            b.taxTotal(), b.cgst(), b.sgst(), b.igst(), b.grandTotal(), b.grandTotal(),
            BigDecimal.ZERO, b.paymentMode(), BillStatus.PAID, b.notes(), b.ledgerEntryId(),
            b.createdAt(), b.createdBy(), b.createdByName());
    }
}