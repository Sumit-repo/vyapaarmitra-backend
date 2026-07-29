package com.vyapaarmitra.api.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

    /**
     * Running balance after each entry, for a page of entries in NEWEST-FIRST order.
     * The balance is cumulative over all history, so a page on its own can't derive it —
     * the caller anchors it with {@code balanceAfterNewest}: the balance immediately
     * after the newest entry on the page (the account's current balance minus everything
     * newer than that entry). Each older entry's balance-after is the next-newer entry's
     * balance-after minus that newer entry's signed amount (CREDIT +, PAYMENT −).
     *
     * @param signedNewestFirst signed amounts in the same newest-first order as the page
     * @param balanceAfterNewest balance immediately after the first (newest) element
     * @return balance-after for each element, in the same newest-first order
     */
    public static List<BigDecimal> runningBalancesDesc(List<BigDecimal> signedNewestFirst,
                                                       BigDecimal balanceAfterNewest) {
        List<BigDecimal> out = new ArrayList<>(signedNewestFirst.size());
        BigDecimal bal = balanceAfterNewest;
        for (int i = 0; i < signedNewestFirst.size(); i++) {
            if (i > 0) {
                bal = bal.subtract(signedNewestFirst.get(i - 1));
            }
            out.add(bal);
        }
        return out;
    }
}
