package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.Account;
import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.domain.Category;
import com.pratosh.moneytracker.domain.Txn;
import com.pratosh.moneytracker.dto.Dtos.ImportResult;
import com.pratosh.moneytracker.repo.CategoryRepository;
import com.pratosh.moneytracker.repo.TxnRepository;
import com.pratosh.moneytracker.util.ApiException;
import com.pratosh.moneytracker.util.Csv;
import com.pratosh.moneytracker.util.Currencies;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CsvService {
    private static final String HEADER = "date,time,type,amount,currency,category,account,payment_method,description,notes";
    private static final int MAX_ROWS = 5000;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final TxnRepository txns;
    private final CategoryRepository categories;
    private final Lookups lookups;

    @Transactional(readOnly = true)
    public String export(AppUser u) {
        Map<Long, Category> cm = lookups.categories(u.getId());
        Map<Long, Account> am = lookups.accounts(u.getId());
        StringBuilder sb = new StringBuilder("\uFEFF").append(HEADER).append("\r\n");
        for (Txn t : txns.findByOwnerIdOrderByOccurredAtDescIdDesc(u.getId())) {
            Category c = t.getCategoryId() == null ? null : cm.get(t.getCategoryId());
            Account a = t.getAccountId() == null ? null : am.get(t.getAccountId());
            sb.append(String.join(",",
                    t.getOccurredAt().toLocalDate().toString(),
                    t.getOccurredAt().toLocalTime().format(TIME),
                    t.getType(),
                    t.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    t.getCurrency(),
                    Csv.escape(c == null ? "" : c.getName()),
                    Csv.escape(a == null ? "" : a.getName()),
                    Csv.escape(t.getPaymentMethod()),
                    Csv.escape(t.getDescription()),
                    Csv.escape(t.getNotes()))).append("\r\n");
        }
        return sb.toString();
    }

    @Transactional
    public ImportResult importCsv(AppUser u, MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.bad("Please choose a CSV file.");
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiException.bad("That file could not be read.");
        }
        List<List<String>> rows = Csv.parse(text);
        if (rows.size() < 2) throw ApiException.bad("The file has no data rows.");
        if (rows.size() - 1 > MAX_ROWS) throw ApiException.bad("Please import at most " + MAX_ROWS + " rows at a time.");

        List<String> header = rows.get(0).stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
        for (String need : List.of("date", "type", "amount")) {
            if (!header.contains(need)) throw ApiException.bad("Missing required column: " + need);
        }

        Map<String, Category> catIndex = new HashMap<>();
        for (Category c : lookups.categories(u.getId()).values()) catIndex.put(c.getKind() + "|" + c.getName().toLowerCase(Locale.ROOT), c);
        Map<String, Long> accIndex = new HashMap<>();
        for (Account a : lookups.accounts(u.getId()).values()) accIndex.put(a.getName().toLowerCase(Locale.ROOT), a.getId());

        List<Txn> toSave = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            int line = i + 1;
            String type = get(cells, header, "type").toLowerCase(Locale.ROOT);
            if (!type.equals("income") && !type.equals("expense")) {
                err(errors, "Row " + line + ": type must be income or expense.");
                skipped++;
                continue;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(get(cells, header, "amount").replace(",", "")).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                err(errors, "Row " + line + ": amount must be a number.");
                skipped++;
                continue;
            }
            if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
                err(errors, "Row " + line + ": amount must be a positive number.");
                skipped++;
                continue;
            }
            LocalDateTime when;
            try {
                String time = get(cells, header, "time");
                when = LocalDateTime.of(LocalDate.parse(get(cells, header, "date")),
                        time.isEmpty() ? LocalTime.NOON : LocalTime.parse(time.length() == 4 ? "0" + time : time));
                if (when.getYear() < 1970 || when.getYear() > 2100) throw new DateTimeParseException("range", time, 0);
            } catch (DateTimeParseException e) {
                err(errors, "Row " + line + ": date could not be read (use YYYY-MM-DD and HH:mm).");
                skipped++;
                continue;
            }

            Txn t = new Txn();
            t.setOwnerId(u.getId());
            t.setType(type);
            t.setAmount(amount);
            String cur = get(cells, header, "currency").toUpperCase(Locale.ROOT);
            t.setCurrency(Currencies.isValid(cur) ? cur : u.getCurrency());
            String catName = clip(get(cells, header, "category"), 40);
            if (!catName.isEmpty()) {
                String key = type + "|" + catName.toLowerCase(Locale.ROOT);
                Category c = catIndex.get(key);
                if (c == null) {
                    c = new Category();
                    c.setOwnerId(u.getId());
                    c.setName(catName);
                    c.setKind(type);
                    c = categories.save(c);
                    catIndex.put(key, c);
                }
                t.setCategoryId(c.getId());
            }
            t.setAccountId(accIndex.get(get(cells, header, "account").toLowerCase(Locale.ROOT)));
            String method = get(cells, header, "payment_method").toLowerCase(Locale.ROOT);
            t.setPaymentMethod(TxnService.METHODS.contains(method) ? method : "cash");
            String desc = clip(get(cells, header, "description"), 200);
            String notes = clip(get(cells, header, "notes"), 1000);
            t.setDescription(desc.isEmpty() ? null : desc);
            t.setNotes(notes.isEmpty() ? null : notes);
            t.setOccurredAt(when);
            toSave.add(t);
        }
        txns.saveAll(toSave);
        return new ImportResult(toSave.size(), skipped, errors);
    }

    private static void err(List<String> errors, String msg) {
        if (errors.size() < 20) errors.add(msg);
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String get(List<String> cells, List<String> header, String name) {
        int idx = header.indexOf(name);
        return idx >= 0 && idx < cells.size() ? cells.get(idx) : "";
    }
}
