package com.chzzk.live;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveSignalingHandler extends TextWebSocketHandler {

    private final LiveRepository liveRepository;
    private final ObjectMapper objectMapper;

    // 방송 id → 방송자 연결
    private final Map<Long, WebSocketSession> broadcasters = new ConcurrentHashMap<>();
    // 시청자 연결 id → 시청자 연결
    private final Map<String, WebSocketSession> viewers = new ConcurrentHashMap<>();

    public LiveSignalingHandler(LiveRepository liveRepository, ObjectMapper objectMapper) {
        this.liveRepository = liveRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> params = UriComponentsBuilder.fromUri(session.getUri())
                .build().getQueryParams().toSingleValueMap();
        Long liveId = Long.valueOf(params.get("liveId"));
        String role = params.get("role");

        Live live = liveRepository.findById(liveId).orElse(null);
        if (live == null || !live.isOnAir()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        session.getAttributes().put("liveId", liveId);
        session.getAttributes().put("role", role);

        if ("broadcaster".equals(role)) {
            if (!live.isOwnedBy(params.get("key"))) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            broadcasters.put(liveId, session);
            // 방송자보다 먼저 들어와 있던 시청자를 알려 준다
            for (WebSocketSession viewer : viewers.values()) {
                if (liveId.equals(viewer.getAttributes().get("liveId"))) {
                    send(session, Map.of("type", "viewer-joined", "viewerId", viewer.getId()));
                }
            }
        } else {
            viewers.put(session.getId(), session);
            send(broadcasters.get(liveId), Map.of("type", "viewer-joined", "viewerId", session.getId()));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ObjectNode msg = (ObjectNode) objectMapper.readTree(message.getPayload());
        Long liveId = (Long) session.getAttributes().get("liveId");

        if ("broadcaster".equals(session.getAttributes().get("role"))) {
            // 방송자 → 특정 시청자 (offer, ice)
            WebSocketSession viewer = viewers.get(msg.get("to").asString());
            if (viewer != null && liveId.equals(viewer.getAttributes().get("liveId"))) {
                send(viewer, msg);
            }
        } else {
            // 시청자 → 방송자 (answer, ice), 누가 보냈는지 붙여서
            msg.put("from", session.getId());
            send(broadcasters.get(liveId), msg);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long liveId = (Long) session.getAttributes().get("liveId");
        if (liveId == null) {
            return;
        }

        if ("broadcaster".equals(session.getAttributes().get("role"))) {
            broadcasters.remove(liveId, session);
            for (WebSocketSession viewer : viewers.values()) {
                if (liveId.equals(viewer.getAttributes().get("liveId"))) {
                    send(viewer, Map.of("type", "ended"));
                }
            }
        } else {
            viewers.remove(session.getId());
            send(broadcasters.get(liveId), Map.of("type", "viewer-left", "viewerId", session.getId()));
        }
    }

    private void send(WebSocketSession target, Object message) throws IOException {
        if (target == null || !target.isOpen()) {
            return;
        }
        String json = objectMapper.writeValueAsString(message);
        // 한 연결에 여러 스레드가 동시에 보내면 오류가 나서 잠근다
        synchronized (target) {
            target.sendMessage(new TextMessage(json));
        }
    }
}