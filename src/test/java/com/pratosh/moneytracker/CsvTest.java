package com.pratosh.moneytracker;

import com.pratosh.moneytracker.util.Csv;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvTest {
    @Test
    void parsesQuotedCommasAndEscapedQuotes() {
        List<List<String>> rows = Csv.parse("a,b\n\"x,y\",\"say \"\"hi\"\"\"\n");
        assertEquals(2, rows.size());
        assertEquals("x,y", rows.get(1).get(0));
        assertEquals("say \"hi\"", rows.get(1).get(1));
    }

    @Test
    void escapeQuotesCommasAndDefusesFormulas() {
        assertEquals("\"a,b\"", Csv.escape("a,b"));
        assertEquals("'=SUM(A1)", Csv.escape("=SUM(A1)"));
        assertEquals("", Csv.escape(null));
    }
}
