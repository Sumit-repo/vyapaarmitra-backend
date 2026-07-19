package com.vyapaarmitra.api.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Pure ledger arithmetic. Payments are allocated FIFO against credits (oldest
 * credit first), which is how shop khata settlements work in practice: money
 * received clears the oldest udhar first.
 */
public final class LedgerMath {

    private LedgerMath() {
    }

    public record CreditLine(BigDecimal amount, LocalDate dueDate) {
    }

    public record LedgerState(BigDecimal balance, LocalDate oldestOpenDueDate,
                              int openOverdueCredits, long overdueDays) {
    }

    /**
     * @param credits credit lines in chronological order
     * @param totalPayments sum of all payments
     * @param today reference date for overdue calculations
     */
    public static LedgerState compute(List<CreditLine> credits, BigDecimal totalPayments,
                                      LocalDate today) {
        BigDecimal totalCredits = credits.stream()
            .map(CreditLine::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = totalCredits.subtract(totalPayments);

        BigDecimal pool = totalPayments;
        LocalDate oldestOpenDueDate = null;
        int openOverdueCredits = 0;
        for (CreditLine credit : credits) {
            if (pool.compareTo(credit.amount()) >= 0) {
                pool = pool.subtract(credit.amount());
                continue;
            }
            // credit not fully covered => open
            pool = BigDecimal.ZERO;
            if (credit.dueDate() != null) {
                if (oldestOpenDueDate == null || credit.dueDate().isBefore(oldestOpenDueDate)) {
                    oldestOpenDueDate = credit.dueDate();
                }
                if (credit.dueDate().isBefore(today)) {
                    openOverdueCredits++;
                }
            }
        }

        long overdueDays = 0;
        if (oldestOpenDueDate != null && oldestOpenDueDate.isBefore(today)) {
            overdueDays = ChronoUnit.DAYS.between(oldestOpenDueDate, today);
        }
        return new LedgerState(balance, oldestOpenDueDate, openOverdueCredits, overdueDays);
    }
}
