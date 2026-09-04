package com.vyapaarmitra.api.dataimport;

/**
 * Which side of the khata an import fills — detected from the OkCredit statement
 * header ("CUSTOMER ACCOUNT STATEMENT" vs "SUPPLIER ACCOUNT STATEMENT").
 */
public enum ImportKind {
    CUSTOMER,
    SUPPLIER
}