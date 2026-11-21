package com.pratosh.moneytracker.web;

import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.dto.Dtos.*;
import com.pratosh.moneytracker.service.CurrentUser;
import com.pratosh.moneytracker.service.DemoService;
import com.pratosh.moneytracker.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MeController {
    private final CurrentUser current;
    private final ProfileService profiles;
    private final DemoService demo;
    private final ObjectMapper mapper;

    @GetMapping("/me")
    public ProfileDto me() {
        return ProfileService.toDto(current.get());
    }

    @PutMapping("/me")
    public ProfileDto update(@Valid @RequestBody ProfileUpdateReq r) {
        return profiles.update(current.get(), r);
    }

    @PutMapping("/me/pin")
    public ResponseEntity<Void> changePin(@Valid @RequestBody PinChangeReq r) {
        profiles.changePin(current.get(), r);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/demo")
    public ProfileDto loadDemo() {
        AppUser u = current.get();
        demo.load(u);
        return ProfileService.toDto(current.get());
    }

    @DeleteMapping("/demo")
    public ProfileDto clearDemo() {
        demo.clear(current.get());
        return ProfileService.toDto(current.get());
    }

    @GetMapping("/export/json")
    public ResponseEntity<byte[]> exportJson() throws Exception {
        byte[] body = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(profiles.exportAll(current.get()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("money-map-" + LocalDate.now() + ".json", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
