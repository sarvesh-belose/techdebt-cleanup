package com.enterprise.techdebt.ingestion.veracode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Computes the {@code Authorization} header for the Veracode REST API per Veracode's
 * HMAC-SHA-256 spec. The algorithm is documented here:
 *   https://docs.veracode.com/r/c_hmac_signing_examples
 *
 * Key derivation chain:
 *   k1 = HMAC(nonce_bytes,    key=apiKey_bytes)
 *   k2 = HMAC(ts_bytes,       key=k1)
 *   k3 = HMAC(VERSION_BYTES,  key=k2)
 *   sig = HMAC(data_bytes,    key=k3)
 *
 * where data = apiId + ts + nonce + httpMethod + uri (UTF-8).
 *
 * Inputs apiId and apiKey are both hex-encoded; nonce is generated per call.
 */
public final class VeracodeHmacSigner {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final String AUTH_SCHEME = "VERACODE-HMAC-SHA-256";
    private static final String VERSION = "vcode_request_version_1";

    private final SecureRandom rng;

    public VeracodeHmacSigner() {
        this(new SecureRandom());
    }

    public VeracodeHmacSigner(SecureRandom rng) {
        this.rng = rng;
    }

    public String authorizationHeader(String apiId, String apiKey, String httpMethod, String uri) {
        return authorizationHeader(apiId, apiKey, httpMethod, uri, System.currentTimeMillis(), generateNonce());
    }

    String authorizationHeader(String apiId, String apiKey, String httpMethod, String uri,
                               long timestampMs, String nonceHex) {
        String data = apiId + timestampMs + nonceHex + httpMethod + uri;
        byte[] apiKeyBytes = hexDecode(apiKey);
        byte[] nonceBytes = hexDecode(nonceHex);

        byte[] k1 = hmac(nonceBytes, apiKeyBytes);
        byte[] k2 = hmac(Long.toString(timestampMs).getBytes(StandardCharsets.UTF_8), k1);
        byte[] k3 = hmac(VERSION.getBytes(StandardCharsets.UTF_8), k2);
        byte[] sig = hmac(data.getBytes(StandardCharsets.UTF_8), k3);

        return AUTH_SCHEME
                + " id=" + apiId
                + ",ts=" + timestampMs
                + ",nonce=" + nonceHex
                + ",sig=" + hexEncode(sig);
    }

    private byte[] hmac(byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(key, HMAC_ALG));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA-256 unavailable", e);
        }
    }

    private String generateNonce() {
        byte[] nonce = new byte[16];
        rng.nextBytes(nonce);
        return hexEncode(nonce);
    }

    static byte[] hexDecode(String hex) {
        if (hex == null) throw new IllegalArgumentException("hex is null");
        if ((hex.length() & 1) != 0) throw new IllegalArgumentException("hex length must be even");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("invalid hex at " + i);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
