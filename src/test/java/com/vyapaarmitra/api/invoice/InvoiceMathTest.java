package com.vyapaarmitra.api.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceMathTest {

    private static InvoiceMath.ItemInput item(String name, String qty, String rate, String taxRate) {
        return new InvoiceMath.ItemInput(name, new BigDecimal(qty), "pcs", new BigDecimal(rate),
            null, taxRate == null ? null : new BigDecimal(taxRate));
    }

    @Test
    void kacchaBillCarriesNoTax() {
        InvoiceMath.Totals t = InvoiceMath.compute(BillType.KACCHA, false, BigDecimal.ZERO,
            List.of(item("Dal", "5", "140", null), item("Oil", "2", "180", null)));

        assertThat(t.subtotal()).isEqualByComparingTo("1060");
        assertThat(t.taxTotal()).isEqualByComparingTo("0");
        assertThat(t.grandTotal()).isEqualByComparingTo("1060");
    }

    @Test
    void pakkaIntraStateSplitsCgstSgst() {
        // 1000 @ 18% GST = 180 tax, split 90 CGST + 90 SGST
        InvoiceMath.Totals t = InvoiceMath.compute(BillType.PAKKA, false, BigDecimal.ZERO,
            List.of(item("Widget", "1", "1000", "18")));

        assertThat(t.taxTotal()).isEqualByComparingTo("180.00");
        assertThat(t.cgst()).isEqualByComparingTo("90.00");
        assertThat(t.sgst()).isEqualByComparingTo("90.00");
        assertThat(t.igst()).isEqualByComparingTo("0.00");
        assertThat(t.grandTotal()).isEqualByComparingTo("1180.00");
    }

    @Test
    void pakkaInterStateChargesIgst() {
        InvoiceMath.Totals t = InvoiceMath.compute(BillType.PAKKA, true, BigDecimal.ZERO,
            List.of(item("Widget", "1", "1000", "18")));

        assertThat(t.igst()).isEqualByComparingTo("180.00");
        assertThat(t.cgst()).isEqualByComparingTo("0.00");
        assertThat(t.grandTotal()).isEqualByComparingTo("1180.00");
    }

    @Test
    void discountReducesTaxableBaseProportionally() {
        // subtotal 1000, discount 100 -> taxable 900 @ 18% = 162 tax
        InvoiceMath.Totals t = InvoiceMath.compute(BillType.PAKKA, false, new BigDecimal("100"),
            List.of(item("Widget", "1", "1000", "18")));

        assertThat(t.taxableValue()).isEqualByComparingTo("900.00");
        assertThat(t.taxTotal()).isEqualByComparingTo("162.00");
        assertThat(t.grandTotal()).isEqualByComparingTo("1062.00");
    }

    @Test
    void mixedSlabsTaxEachItemAtItsOwnRate() {
        // 2000 @ 5% = 100, 1000 @ 12% = 120 -> 220 total tax
        InvoiceMath.Totals t = InvoiceMath.compute(BillType.PAKKA, false, BigDecimal.ZERO,
            List.of(item("Rice", "1", "2000", "5"), item("Namkeen", "1", "1000", "12")));

        assertThat(t.taxTotal()).isEqualByComparingTo("220.00");
        assertThat(t.grandTotal()).isEqualByComparingTo("3220.00");
    }

    @Test
    void deriveStatusFromReceived() {
        assertThat(InvoiceMath.deriveStatus(new BigDecimal("100"), BigDecimal.ZERO)).isEqualTo(BillStatus.UNPAID);
        assertThat(InvoiceMath.deriveStatus(new BigDecimal("100"), new BigDecimal("40"))).isEqualTo(BillStatus.PARTIAL);
        assertThat(InvoiceMath.deriveStatus(new BigDecimal("100"), new BigDecimal("100"))).isEqualTo(BillStatus.PAID);
    }
}
