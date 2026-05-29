package com.enterprise.techdebt.credentials;

import com.enterprise.techdebt.ingestion.veracode.VeracodeHmacSigner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VeracodeCredentialValidatorTest {

    @Test
    void rejectsBadFormat() {
        var v = new VeracodeCredentialValidator(mock(HttpClient.class), new VeracodeHmacSigner());
        var r1 = v.validate("https://api.veracode.com", "no-colon-here");
        assertThat(r1.ok()).isFalse();
        assertThat(r1.status()).isEqualTo("BAD_FORMAT");

        var r2 = v.validate("https://api.veracode.com", "xyz:notHex");
        assertThat(r2.ok()).isFalse();
        assertThat(r2.status()).isEqualTo("BAD_FORMAT");
    }

    @Test
    void passesOn2xx() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        doReturn(resp).when(client).send(any(HttpRequest.class), any());

        var v = new VeracodeCredentialValidator(client, new VeracodeHmacSigner());
        var result = v.validate("https://api.veracode.com", "abcdef0123456789:00112233445566778899aabbccddeeff");

        assertThat(result.ok()).isTrue();
        assertThat(result.status()).isEqualTo("OK");

        ArgumentCaptor<HttpRequest> reqCap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(reqCap.capture(), any());
        HttpRequest sent = reqCap.getValue();
        assertThat(sent.uri().toString()).contains("/appsec/v1/applications");
        assertThat(sent.headers().firstValue("Authorization").orElseThrow())
                .startsWith("VERACODE-HMAC-SHA-256 id=abcdef0123456789,ts=");
    }

    @Test
    void mapsAuthFailures() throws Exception {
        for (int status : new int[]{401, 403}) {
            HttpClient client = mock(HttpClient.class);
            @SuppressWarnings("unchecked")
            HttpResponse<String> resp = mock(HttpResponse.class);
            when(resp.statusCode()).thenReturn(status);
            doReturn(resp).when(client).send(any(HttpRequest.class), any());

            var v = new VeracodeCredentialValidator(client, new VeracodeHmacSigner());
            var r = v.validate("https://api.veracode.com", "abcdef0123456789:00112233445566778899aabbccddeeff");
            assertThat(r.ok()).isFalse();
            assertThat(r.status()).as("HTTP %d", status).isEqualTo("BAD_CREDENTIALS");
        }
    }

    @Test
    void mapsNetworkFailureToUnreachable() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doThrow(new ConnectException("connection refused")).when(client).send(any(HttpRequest.class), any());
        var v = new VeracodeCredentialValidator(client, new VeracodeHmacSigner());

        var r = v.validate("https://api.veracode.com", "abcdef0123456789:00112233445566778899aabbccddeeff");
        assertThat(r.ok()).isFalse();
        assertThat(r.status()).isEqualTo("UNREACHABLE");
    }

    @Test
    void otherIoErrorAlsoMapsToUnreachable() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("timeout")).when(client).send(any(HttpRequest.class), any());
        var v = new VeracodeCredentialValidator(client, new VeracodeHmacSigner());
        var r = v.validate("https://api.veracode.com", "abcdef0123456789:00112233445566778899aabbccddeeff");
        assertThat(r.ok()).isFalse();
        assertThat(r.status()).isEqualTo("UNREACHABLE");
    }
}
