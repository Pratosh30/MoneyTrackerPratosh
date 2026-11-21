package com.pratosh.moneytracker;

import com.pratosh.moneytracker.domain.Txn;
import com.pratosh.moneytracker.dto.Dtos.Summary;
import com.pratosh.moneytracker.service.Analytics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsTest {
    private static Txn txn(String type, String amount) {
        Txn t = new Txn();
        t.setType(type);
        t.setAmount(new BigDecimal(amount));
        t.setOccurredAt(LocalDateTime.now());
        return t;
    }

    @Test
    void summaryUsesExactDecimals() {
        Summary s = Analytics.summarize(List.of(txn("income", "1000.10"), txn("expense", "0.10"), txn("expense", "0.20")));
        assertEquals(new BigDecimal("1000.10"), s.income());
        assertEquals(new BigDecimal("0.30"), s.expense());
        assertEquals(new BigDecimal("999.80"), s.net());
        assertEquals(3, s.count());
    }

    @Test
    void savingsRateIsNullWithoutIncome() {
        assertNull(Analytics.summarize(List.of(txn("expense", "5.00"))).savingsRate());
    }

    @Test
    void budgetStatusLevels() {
        assertEquals("unused", Analytics.status(BigDecimal.ZERO, new BigDecimal("100")));
        assertEquals("on-track", Analytics.status(new BigDecimal("50"), new BigDecimal("100")));
        assertEquals("approaching", Analytics.status(new BigDecimal("80"), new BigDecimal("100")));
        assertEquals("exceeded", Analytics.status(new BigDecimal("100.01"), new BigDecimal("100")));
    }

    @Test
    void pctChangeIsNullWhenNothingToCompare() {
        assertNull(Analytics.pctChange(new BigDecimal("10"), BigDecimal.ZERO));
        assertEquals(50.0, Analytics.pctChange(new BigDecimal("150"), new BigDecimal("100")));
    }
}
