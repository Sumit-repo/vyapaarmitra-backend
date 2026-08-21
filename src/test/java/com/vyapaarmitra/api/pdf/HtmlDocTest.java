package com.vyapaarmitra.api.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HtmlDocTest {

    @Test
    void rupeesUsesIndianGrouping() {
        assertEquals("₹0", HtmlDoc.rupees(BigDecimal.ZERO));
        assertEquals("₹250", HtmlDoc.rupees(new BigDecimal("250")));
        assertEquals("₹1,000", HtmlDoc.rupees(new BigDecimal("1000")));
        assertEquals("₹44,700", HtmlDoc.rupees(new BigDecimal("44700")));
        assertEquals("₹1,30,945", HtmlDoc.rupees(new BigDecimal("130945")));
        assertEquals("₹1,75,145", HtmlDoc.rupees(new BigDecimal("175145")));
        assertEquals("₹1,29,25,200", HtmlDoc.rupees(new BigDecimal("12925200")));
    }

    @Test
    void rupeesRoundsAndHandlesNegatives() {
        assertEquals("₹1,235", HtmlDoc.rupees(new BigDecimal("1234.60")));
        assertEquals("-₹500", HtmlDoc.rupees(new BigDecimal("-500")));
    }
}
