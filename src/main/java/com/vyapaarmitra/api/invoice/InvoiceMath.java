package com.vyapaarmitra.api.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative bill money. The client computes the same figures for a live
 * preview, but the server recomputes on save so totals can never be tampered
 * with. Kaccha bills carry no tax. On pakka bills the flat discount is spread
 * across items in proportion to their value, then each item's share is taxed at
 * its own GST slab — so a mixed-slab bill still totals correctly. Intra-state
 * splits the tax into equal CGST + SGST halves; inter-state charges it as IGST.
 */
public final class InvoiceMath {

    private InvoiceMath() {
    }

    private static final int SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Raw line as submitted by the client (no computed amount). */
    public record ItemInput(String name, BigDecimal qty, String unit, BigDecimal rate,
                            String hsn, BigDecimal taxRate) {
    }

    public record Totals(List<InvoiceItemJson> items, BigDecimal subtotal, BigDecimal discount,
                         BigDecimal taxableValue, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                         BigDecimal taxTotal, BigDecimal grandTotal) {
    }

    public static Totals compute(BillType type, boolean interState, BigDecimal discountInput,
                                 List<ItemInput> inputs) {
        List<InvoiceItemJson> items = new ArrayList<>(inputs.size());
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemInput in : inputs) {
            BigDecimal qty = nz(in.qty());
            BigDecimal rate = nz(in.rate());
            BigDecimal line = qty.multiply(rate).setScale(SCALE, RM);
            subtotal = subtotal.add(line);
            BigDecimal taxRate = type == BillType.PAKKA ? nz(in.taxRate()) : null;
            items.add(new InvoiceItemJson(in.name(), qty, in.unit(), rate, in.hsn(), taxRate, line));
        }
        subtotal = subtotal.setScale(SCALE, RM);

        BigDecimal discount = clamp(nz(discountInput), subtotal);
        BigDecimal taxableValue = subtotal.subtract(discount).setScale(SCALE, RM);

        BigDecimal taxTotal = BigDecimal.ZERO;
        if (type == BillType.PAKKA && subtotal.signum() > 0) {
            for (InvoiceItemJson it : items) {
                BigDecimal share = it.lineAmount();
                if (share.signum() == 0) {
                    continue;
                }
                // item's slice of the taxable base after proportional discount
                BigDecimal taxableShare = share.subtract(
                    discount.multiply(share).divide(subtotal, 6, RM));
                BigDecimal rate = nz(it.taxRate());
                taxTotal = taxTotal.add(taxableShare.multiply(rate).divide(HUNDRED, 6, RM));
            }
        }
        taxTotal = taxTotal.setScale(SCALE, RM);

        boolean inter = type == BillType.PAKKA && interState;
        BigDecimal cgst = inter ? zero() : half(taxTotal);
        BigDecimal sgst = inter ? zero() : taxTotal.subtract(cgst).setScale(SCALE, RM);
        BigDecimal igst = inter ? taxTotal : zero();

        BigDecimal grandTotal = taxableValue.add(taxTotal).setScale(SCALE, RM);

        return new Totals(items, subtotal, discount, taxableValue, cgst, sgst, igst, taxTotal, grandTotal);
    }

    /** PAID / PARTIAL / UNPAID from the grand total vs what was received. */
    public static BillStatus deriveStatus(BigDecimal grandTotal, BigDecimal amountReceived) {
        BigDecimal received = nz(amountReceived);
        if (received.signum() <= 0) {
            return BillStatus.UNPAID;
        }
        return received.compareTo(grandTotal) >= 0 ? BillStatus.PAID : BillStatus.PARTIAL;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RM);
    }

    private static BigDecimal half(BigDecimal v) {
        return v.divide(BigDecimal.valueOf(2), SCALE, RM);
    }

    private static BigDecimal clamp(BigDecimal v, BigDecimal max) {
        BigDecimal lo = v.max(BigDecimal.ZERO);
        return lo.min(max).setScale(SCALE, RM);
    }
}
