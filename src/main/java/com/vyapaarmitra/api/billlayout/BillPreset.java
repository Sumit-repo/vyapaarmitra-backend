package com.vyapaarmitra.api.billlayout;

/**
 * The bill-design presets. {@code CLASSIC} is the original free bill and stays the default
 * (an absent {@code bill_layouts} row means CLASSIC too), so no existing bill changes for
 * FREE shops. MINIMAL and BOLD are Pro. Declaration order is the order the API lists them
 * in {@code available}.
 */
public enum BillPreset {
    CLASSIC("Classic", false),
    MINIMAL("Minimal", true),
    BOLD("Bold", true);

    private final String label;
    private final boolean pro;

    BillPreset(String label, boolean pro) {
        this.label = label;
        this.pro = pro;
    }

    /** Human label shown in the designer's preset cards (web/mobile mirror this). */
    public String label() {
        return label;
    }

    /** Whether the preset needs the BILL_LAYOUTS entitlement. */
    public boolean pro() {
        return pro;
    }
}