package com.chzzk.live;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IceControllerTest {

    @Test
    void credentialMatchesCoturnFormula() {
        // openssl dgst -sha1 -hmac secret -binary <<< "1700000000:chzzk" | base64 와 같은 값
        assertEquals("95dEvO3VmJvJCuSEa9TvQXKOD4w=", IceController.credential("secret", "1700000000:chzzk"));
    }

    @Test
    void onlyStunWithoutTurnConfig() {
        List<?> servers = (List<?>) new IceController("", "").iceServers().get("iceServers");
        assertEquals(1, servers.size());
    }

    @Test
    void addsTurnWhenConfigured() {
        List<?> servers = (List<?>) new IceController("turn:1.2.3.4:3478", "s").iceServers().get("iceServers");
        assertEquals(2, servers.size());
        assertEquals(true, ((Map<?, ?>) servers.get(1)).get("username").toString().endsWith(":chzzk"));
    }
}
