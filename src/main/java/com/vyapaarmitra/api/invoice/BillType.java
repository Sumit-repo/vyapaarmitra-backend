package com.vyapaarmitra.api.invoice;

/**
 * The two bills Indian counter shops issue. KACCHA = informal estimate / cash
 * memo (no GST). PAKKA = GST tax invoice (HSN + CGST/SGST or IGST, legally valid).
 */
public enum BillType {
    KACCHA,
    PAKKA
}
