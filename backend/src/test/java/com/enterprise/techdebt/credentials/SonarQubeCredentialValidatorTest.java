package com.enterprise.techdebt.credentials;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarQubeCredentialValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsBadUrlFormat() {
        var v = new SonarQubeCredentialValidator(mock(HttpClient.class), objectMapper);
        var r = v.validate("not-a-url", "token");
        assertThat(r.ok()).isFalse();
        assertThat(r.status()).isEqualTo("BAD_FORMAT");
    }

    @Test
    void passesWhenPingAndValidateBothOk() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            if (req.uri().getPath().endsWith("/api/system/ping")) {
                return mockResp(200, "pong");
            }
            return mockResp(200, "{\"valid\":true}");
        });
        var v = new SonarQubeCredentialValidator(client, objectMapper);
        var r = v.validate("https://sonar.acme.com", "tok123");
        assertThat(r.ok()).isTrue();
        assertThat(r.message()).contains("validated the token");
    }

    @Test
    void pingFailureReportsUnreachable() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doThrow(new ConnectException("connection refused"))
                .when(client).send(any(HttpRequest.class), any());
        var v = new SonarQubeCredentialValidator(client, objectMapper);
        var r = v.validate("https://sonar.acme.com", "tok123");
        assertThat(r.ok()).isFalse();
        assertThat(r.status()).isEqualTo("UNREACHABLE");
    }

    @Test
    void wrongHostThatRespondsButNotPongIsUnreachable() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any()))
                .thenAnswer(inv -> mockResp(200, "Welcome to nginx!"));
        var v = new SonarQubeCredentialValidator(client, objectMapper);
        var r = v.validate("https://sonar.acme.com", "tok123");
        assertThat(r.ok()).isFalse();
        assertThat(r.status()).isEqualTo("UNREACHABLE");
    }

    @Test
    void rejectedTokenMapsToBadCredentials() throws Exception {
        HttpClient client = mock(HttpClient.class);
        List<HttpResponse<String>> queue = new ArrayList<>();
        queue.add(mockResp(200, "pong"));
        queue.add(mockResp(401, ""));
        when(client.send(any(HttpRequest.class), any())).thenAnswer(inv -> queue.remove(0));

        var v = new SonarQubeCredentialValidator(client, objectMapper);
        var r = v.validate("https://sonar.acme.com", "bad-token");
        assertThat(r.ok()).isFalse();
        assertThat(r.status()).isEqualTo("BAD_CREDENTIALS");
    }

    @Test
    void validFalseBodyAlsoMapsToBadCredentials() throws Exception {
        HttpClient client = mock(HttpClient.class);
        List<HttpResponse<String>> queue = new ArrayList<>();
        queue.add(mockResp(200, "pong"));
        queue.add(mockResp(200, "{\"valid\":false}"));
        when(client.send(any(HttpRequest.class), any())).thenAnswer(inv -> queue.remove(0));

        var v = new SonarQubeCredentialValidator(client, objectMapper);
        var r = v.validate("https://sonar.acme.com", "expired-token");
        assertThat(r.ok()).isFalse();
        assertThat(r.status()).isEqualTo("BAD_CREDENTIALS");
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResp(int status, String body) {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        return resp;
    }
}
