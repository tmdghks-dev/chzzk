package com.chzzk.donation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class TossClient {
    private final RestClient restClient;

    public TossClient(@Value("${toss.secret-key}") String secretKey) {
        String auth = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.tosspayments.com")
                .defaultHeader("Authorization", "Basic " + auth)
                .build();
    }

    public Map confirm(String paymentKey, String orderId, long amount) {
        return restClient.post()
                .uri("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                .retrieve()
                .body(Map.class);
    }
}