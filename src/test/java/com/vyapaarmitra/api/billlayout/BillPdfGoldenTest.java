package com.vyapaarmitra.api.billlayout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vyapaarmitra.api.invoice.BillPdfHtml;
import com.vyapaarmitra.api.invoice.BillType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Golden test for the CLASSIC preset: pins the exact XHTML the bill PDF used before the
 * bill-design refactor. If this breaks, every existing bill PDF changed — so a preset
 * restyle must NEVER touch {@code ClassicBillLayout}. The pinned files live in
 * {@code src/test/resources/billlayout/} and were captured from the pre-refactor builder.
 */
class BillPdfGoldenTest {

    @ParameterizedTest
    @EnumSource(BillType.class)
    void classicOutputIsByteIdenticalToPreRefactor(BillType type) throws IOException {
        String expected = golden("golden-classic-" + type.name().toLowerCase() + ".html");
        BillPdfData classic = new BillPdfData(BillSamples.bill(type), "Sharma Kirana Store",
            null, null, null, true, BillPreset.CLASSIC, BillLayoutOptions.defaults());
        assertEquals(expected, BillPdfHtml.build(classic),
            () -> "CLASSIC markup drifted for " + type + " — existing bills must not change.");
    }

    private static String golden(String name) throws IOException {
        try (InputStream in = BillPdfGoldenTest.class.getResourceAsStream("/billlayout/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing golden resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Guards the fixture itself: both samples carry money on every builder branch. */
    @Test
    void fixtureHasBalanceAndTax() {
        BillSamples.assertConsistent(BillSamples.bill(BillType.PAKKA));
        BillSamples.assertConsistent(BillSamples.bill(BillType.KACCHA));
    }
}