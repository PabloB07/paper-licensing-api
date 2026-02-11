package com.blancocl.licensing.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class HmacLicenseSigner {
    private static final String HMAC_ALG = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final byte[] secret;

    public HmacLicenseSigner(String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("license signing secret must be at least 16 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generate(String pluginId) {
        String nonce = randomNonce(20);
        String payload = normalizePluginId(pluginId) + ":" + nonce;
        String signature = sign(payload);
        return nonce + "." + signature;
    }

    public boolean verify(String pluginId, String key) {
        int sep = key.lastIndexOf('.');
        if (sep <= 0 || sep == key.length() - 1) {
            return false;
        }
        String nonce = key.substring(0, sep);
        String incomingSig = key.substring(sep + 1);
        String expectedSig = sign(normalizePluginId(pluginId) + ":" + nonce);
        return constantTimeEquals(incomingSig, expectedSig);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret, HMAC_ALG));
            byte[] full = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] truncated = new byte[16];
            System.arraycopy(full, 0, truncated, 0, truncated.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not create HMAC", e);
        }
    }

    private String randomNonce(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    private String normalizePluginId(String pluginId) {
        return pluginId == null ? "" : pluginId.trim().toLowerCase();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
