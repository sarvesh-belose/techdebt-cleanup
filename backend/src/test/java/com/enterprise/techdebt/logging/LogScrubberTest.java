package com.enterprise.techdebt.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogScrubberTest {

    @Test
    void masksBearerTokens() {
        String out = LogScrubber.scrub("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signaturedata");
        assertThat(out).contains("Bearer eyJhbG***");
        assertThat(out).doesNotContain("signaturedata");
    }

    @Test
    void masksVeracodeHmacSig() {
        String header = "VERACODE-HMAC-SHA-256 id=abc,ts=1700000000000,nonce=deadbeef,sig=" + "a".repeat(64);
        String out = LogScrubber.scrub(header);
        assertThat(out).contains("sig=aaaaaa***");
        assertThat(out).doesNotContain("a".repeat(64));
    }

    @Test
    void redactsDiffHunks() {
        String diffLike = "context line\n+ public void deleted = true\n- if (auth) { return; }\nmore context";
        String out = LogScrubber.scrub(diffLike);
        assertThat(out).doesNotContain("public void deleted");
        assertThat(out).doesNotContain("if (auth)");
        assertThat(out).contains("«source-line redacted»");
    }

    @Test
    void masksKeyValueSecrets() {
        String out = LogScrubber.scrub("password=hunter2 api_key=xyzABC123");
        assertThat(out).doesNotContain("hunter2");
        assertThat(out).contains("password=***");
        assertThat(out).contains("api_key=***");
    }

    @Test
    void leavesSafeStringsAlone() {
        String safe = "project=payments-svc status=OPEN count=5";
        assertThat(LogScrubber.scrub(safe)).isEqualTo(safe);
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(LogScrubber.scrub(null)).isNull();
        assertThat(LogScrubber.scrub("")).isEmpty();
    }
}
