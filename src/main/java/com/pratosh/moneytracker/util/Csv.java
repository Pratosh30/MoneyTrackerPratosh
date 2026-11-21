package com.pratosh.moneytracker.util;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 reader/writer helpers. */
public final class Csv {
    private Csv() {
    }

    /** Quotes a value when needed and defuses spreadsheet formulas (=, +, -, @ at the start). */
    public static String escape(String value) {
        String s = value == null ? "" : value;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static List<List<String>> parse(String text) {
        if (text != null && text.startsWith("\uFEFF")) text = text.substring(1);
        List<List<String>> rows = new ArrayList<>();
        if (text == null) return rows;
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cur.toString().trim());
                cur.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                row.add(cur.toString().trim());
                cur.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0 || !row.isEmpty()) {
            row.add(cur.toString().trim());
            rows.add(row);
        }
        rows.removeIf(r -> r.stream().allMatch(String::isEmpty));
        return rows;
    }
}
