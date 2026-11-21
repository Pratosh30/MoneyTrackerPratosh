package com.pratosh.moneytracker.web;

import com.pratosh.moneytracker.dto.Dtos.*;
import com.pratosh.moneytracker.service.CsvService;
import com.pratosh.moneytracker.service.CurrentUser;
import com.pratosh.moneytracker.service.TxnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TxnController {
    private final CurrentUser current;
    private final TxnService service;
    private final CsvService csv;

    @GetMapping
    public PageDto<TxnDto> search(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) Long categoryId,
                                  @RequestParam(required = false) Long accountId,
                                  @RequestParam(required = false) String paymentMethod,
                                  @RequestParam(required = false) String month,
                                  @RequestParam(required = false) Integer year,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(defaultValue = "date") String sort,
                                  @RequestParam(defaultValue = "desc") String dir,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return service.search(current.get(), new TxnService.Filter(q, type, categoryId, accountId, paymentMethod,
                month, year, from, to, sort, dir, page, size));
    }

    @PostMapping
    public TxnDto create(@Valid @RequestBody TxnReq r) {
        return service.create(current.get(), r);
    }

    @PutMapping("/{id}")
    public TxnDto update(@PathVariable Long id, @Valid @RequestBody TxnReq r) {
        return service.update(current.get(), id, r);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(current.get(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        byte[] body = csv.export(current.get()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("transactions-" + LocalDate.now() + ".csv", StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(body);
    }

    @PostMapping("/import")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) {
        return csv.importCsv(current.get(), file);
    }
}
