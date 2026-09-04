package com.vyapaarmitra.api.dataimport;

/**
 * Where imported data came from. The statement parser is strategy-shaped so a
 * {@code CSV} (or Khatabook) source can land beside {@code OKCREDIT} later.
 */
public enum ImportSource {
    OKCREDIT
}