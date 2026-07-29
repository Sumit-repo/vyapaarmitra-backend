package com.vyapaarmitra.api.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.vyapaarmitra.api.ledger.LedgerMath.CreditLine;
import com.vyapaarmitra.api.ledger.LedgerMath.LedgerState;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerMathTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 19);

    @Test
    void noEntriesMeansZeroBalance() {
        LedgerState state = LedgerMath.compute(List.of(), BigDecimal.ZERO, TODAY);
        assertThat(state.balance()).isEqualByComparingTo("0");
        assertThat(state.oldestOpenDueDate()).isNull();
        assertThat(state.overdueDays()).isZero();
    }

    @Test
    void runningBalancesWalkBackFromTheNewestEntry() {
        // Page is newest-first. Newest entry left a balance of 250; walking to older
        // entries subtracts each newer entry's signed amount in turn.
        // signed (newest→oldest): +100 (credit), -50 (payment), +200 (credit)
        // balanceAfter:            250,           250-100=150,    150-(-50)=200
        List<BigDecimal> signedNewestFirst = List.of(
            new BigDecimal("100"), new BigDecimal("-50"), new BigDecimal("200"));
        List<BigDecimal> balances = LedgerMath.runningBalancesDesc(signedNewestFirst, new BigDecimal("250"));
        assertThat(balances).containsExactly(
            new BigDecimal("250"), new BigDecimal("150"), new BigDecimal("200"));
    }

    @Test
    void runningBalancesHandleAnEmptyPage() {
        assertThat(LedgerMath.runningBalancesDesc(List.of(), new BigDecimal("99"))).isEmpty();
    }

    @Test
    void runningBalancesSingleEntryIsTheAnchor() {
        assertThat(LedgerMath.runningBalancesDesc(List.of(new BigDecimal("500")), new BigDecimal("500")))
            .containsExactly(new BigDecimal("500"));
    }

    @Test
    void paymentsClearOldestCreditFirst() {
        List<CreditLine> credits = List.of(
            new CreditLine(new BigDecimal("100"), TODAY.minusDays(40)),
            new CreditLine(new BigDecimal("200"), TODAY.minusDays(10)));
        // 100 fully covers the first credit; second remains open and overdue by 10 days
        LedgerState state = LedgerMath.compute(credits, new BigDecimal("100"), TODAY);
        assertThat(state.balance()).isEqualByComparingTo("200");
        assertThat(state.oldestOpenDueDate()).isEqualTo(TODAY.minusDays(10));
        assertThat(state.overdueDays()).isEqualTo(10);
        assertThat(state.openOverdueCredits()).isEqualTo(1);
    }

    @Test
    void partialPaymentKeepsCreditOpen() {
        List<CreditLine> credits = List.of(new CreditLine(new BigDecimal("500"), TODAY.minusDays(5)));
        LedgerState state = LedgerMath.compute(credits, new BigDecimal("300"), TODAY);
        assertThat(state.balance()).isEqualByComparingTo("200");
        assertThat(state.oldestOpenDueDate()).isEqualTo(TODAY.minusDays(5));
        assertThat(state.overdueDays()).isEqualTo(5);
    }

    @Test
    void futureDueDateIsOpenButNotOverdue() {
        List<CreditLine> credits = List.of(new CreditLine(new BigDecimal("500"), TODAY.plusDays(10)));
        LedgerState state = LedgerMath.compute(credits, BigDecimal.ZERO, TODAY);
        assertThat(state.oldestOpenDueDate()).isEqualTo(TODAY.plusDays(10));
        assertThat(state.overdueDays()).isZero();
        assertThat(state.openOverdueCredits()).isZero();
    }

    @Test
    void overpaymentGivesNegativeBalanceAndNoOpenCredits() {
        List<CreditLine> credits = List.of(new CreditLine(new BigDecimal("100"), TODAY.minusDays(30)));
        LedgerState state = LedgerMath.compute(credits, new BigDecimal("150"), TODAY);
        assertThat(state.balance()).isEqualByComparingTo("-50");
        assertThat(state.oldestOpenDueDate()).isNull();
        assertThat(state.overdueDays()).isZero();
    }
}
