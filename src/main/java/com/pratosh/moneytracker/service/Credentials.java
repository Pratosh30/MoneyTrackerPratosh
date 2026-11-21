package com.pratosh.moneytracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * A 4-6 digit PIN is short, so it is never hashed on its own: it is first combined with the
 * User ID and a server-side secret ("pepper") using HMAC-SHA256, and the result is what BCrypt hashes.
 */
@Component
public class Credentials {
    private final byte[] pepper;

    public Credentials(@Value("${app.security.pepper}") String pepper) {
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String of(String userKey, String pin) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            byte[] out = mac.doFinal((userKey + ":" + pin).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out); // 64 chars, under BCrypt's 72-byte limit
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive credential", e);
        }
    }
}
