package com.chzzk.live;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 브라우저가 WebRTC 연결에 쓸 ICE 서버 목록.
 * 셀룰러(통신사 NAT) 뒤에서는 직접 연결이 안 되므로 TURN 서버가 영상을 중계한다.
 * TURN 비밀번호는 coturn의 use-auth-secret 방식: 12시간짜리 임시 계정을 그때그때 만들어 준다.
 */
@RestController
public class IceController {

    static final long TTL_SECONDS = 12 * 60 * 60;

    private final String turnUrl;
    private final String turnSecret;

    public IceController(@Value("${turn.url:}") String turnUrl, @Value("${turn.secret:}") String turnSecret) {
        this.turnUrl = turnUrl;
        this.turnSecret = turnSecret;
    }

    @GetMapping("/api/ice")
    public Map<String, Object> iceServers() {
        List<Map<String, Object>> servers = new ArrayList<>();
        servers.add(Map.of("urls", "stun:stun.l.google.com:19302"));
        if (!turnUrl.isBlank() && !turnSecret.isBlank()) {
            String username = (System.currentTimeMillis() / 1000 + TTL_SECONDS) + ":chzzk";
            servers.add(Map.of(
                    "urls", List.of(turnUrl + "?transport=udp", turnUrl + "?transport=tcp"),
                    "username", username,
                    "credential", credential(turnSecret, username)));
        }
        return Map.of("iceServers", servers);
    }

    static String credential(String secret, String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
