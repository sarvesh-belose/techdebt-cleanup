package com.enterprise.techdebt.ingestion.veracode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VeracodeHmacSignerTest {

    private final VeracodeHmacSigner signer = new VeracodeHmacSigner();

    @Test
    void headerIsDeterministicGivenFixedInputs() {
        String header = signer.authorizationHeader(
                "abcdef0123456789",          // apiId
                "00112233445566778899aabb",  // apiKey (hex)
                "GET",
                "/srcclr/v3/workspaces/abc/issues",
                1700000000000L,
                "deadbeefcafebabe"
        );
        assertThat(header).startsWith("VERACODE-HMAC-SHA-256 ");
        assertThat(header).contains("id=abcdef0123456789");
        assertThat(header).contains("ts=1700000000000");
        assertThat(header).contains("nonce=deadbeefcafebabe");
        assertThat(header).matches(".*sig=[0-9a-f]{64}$");
    }

    @Test
    void differentInputsProduceDifferentSignatures() {
        String h1 = signer.authorizationHeader("id1", "00112233",
                "GET", "/x", 1L, "aabb");
        String h2 = signer.authorizationHeader("id1", "00112233",
                "GET", "/y", 1L, "aabb");
        assertThat(extractSig(h1)).isNotEqualTo(extractSig(h2));
    }

    @Test
    void rejectsOddLengthHex() {
        assertThatThrownBy(() -> VeracodeHmacSigner.hexDecode("abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String extractSig(String header) {
        int idx = header.indexOf("sig=");
        return header.substring(idx + 4);
    }
}
