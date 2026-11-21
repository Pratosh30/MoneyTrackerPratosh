package com.pratosh.moneytracker.util;

import java.util.List;

public final class Currencies {
    private Currencies() {
    }

    public record Meta(String code, String name, String symbol, String locale, int decimals) {
    }

    public static final List<Meta> ALL = List.of(
            new Meta("INR", "Indian Rupee", "₹", "en-IN", 2),
            new Meta("USD", "US Dollar", "$", "en-US", 2),
            new Meta("EUR", "Euro", "€", "de-DE", 2),
            new Meta("GBP", "British Pound", "£", "en-GB", 2),
            new Meta("JPY", "Japanese Yen", "¥", "ja-JP", 0),
            new Meta("CAD", "Canadian Dollar", "CA$", "en-CA", 2),
            new Meta("AUD", "Australian Dollar", "A$", "en-AU", 2),
            new Meta("SGD", "Singapore Dollar", "S$", "en-SG", 2),
            new Meta("AED", "UAE Dirham", "د.إ", "en-AE", 2),
            new Meta("CHF", "Swiss Franc", "CHF", "de-CH", 2),
            new Meta("CNY", "Chinese Yuan", "¥", "zh-CN", 2),
            new Meta("HKD", "Hong Kong Dollar", "HK$", "en-HK", 2),
            new Meta("NZD", "New Zealand Dollar", "NZ$", "en-NZ", 2),
            new Meta("ZAR", "South African Rand", "R", "en-ZA", 2),
            new Meta("BRL", "Brazilian Real", "R$", "pt-BR", 2),
            new Meta("SEK", "Swedish Krona", "kr", "sv-SE", 2),
            new Meta("MYR", "Malaysian Ringgit", "RM", "ms-MY", 2),
            new Meta("THB", "Thai Baht", "฿", "th-TH", 2),
            new Meta("KRW", "South Korean Won", "₩", "ko-KR", 0),
            new Meta("SAR", "Saudi Riyal", "SR", "en-SA", 2)
    );

    public static boolean isValid(String code) {
        return code != null && ALL.stream().anyMatch(c -> c.code().equals(code));
    }
}
