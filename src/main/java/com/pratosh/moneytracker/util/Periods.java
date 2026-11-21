package com.pratosh.moneytracker.util;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class Periods {
    private Periods() {
    }

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    /** Parses "2026-11"; blank means the current month. */
    public static YearMonth month(String value) {
        if (value == null || value.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.bad("Month must look like 2026-11.");
        }
    }

    public static int year(Integer value) {
        int y = value == null ? Year.now().getValue() : value;
        if (y < 1970 || y > 2100) throw ApiException.bad("Year must be between 1970 and 2100.");
        return y;
    }

    public static String shortLabel(YearMonth ym) {
        return ym.format(SHORT);
    }

    public static String longLabel(YearMonth ym) {
        return ym.format(LONG);
    }

    public static LocalDate firstOfYear(int year) {
        return LocalDate.of(year, 1, 1);
    }
}
