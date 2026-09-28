package com.bts.viewplustest;

import org.springframework.stereotype.Component;
import com.bts.viewplustest.chat.ChatMessage;
import com.bts.viewplustest.chat.ChatMessageRepository;
import com.bts.viewplustest.room.LiveRoom;
import com.bts.viewplustest.room.LiveRoomRepository;
import com.bts.viewplustest.room.RoomAccessService;
import com.bts.viewplustest.user.User;
import com.bts.viewplustest.user.UserRepository;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SignalingHandler extends TextWebSocketHandler {

    /**
     * WebRTC 미디어를 직접 중계하지 않고 방 관리와 SDP/ICE/채팅 메시지만 전달한다.
     * 방 하나는 화면 송신자 1명과 시청자 최대 3명으로 구성된다.
     */
    private static final int MAX_VIEWERS_PER_ROOM = 3;
    private static final String CLIENT_ID_ATTRIBUTE = "clientId";
    private static final String ROOM_ID_ATTRIBUTE = "roomId";
    private static final String ROLE_ATTRIBUTE = "role";

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final WebRtcIceProperties webRtcIceProperties;
    private final ChatMessageRepository chatMessages;
    private final LiveRoomRepository liveRooms;
    private final UserRepository users;
    private final RoomAccessService roomAccess;

    private final Map<String, WebSocketSession> clients =
            new ConcurrentHashMap<>();

    private final Map<String, Room> rooms =
            new ConcurrentHashMap<>();

    public SignalingHandler(WebRtcIceProperties webRtcIceProperties, ChatMessageRepository chatMessages, LiveRoomRepository liveRooms, UserRepository users, RoomAccessService roomAccess) {
        this.webRtcIceProperties = webRtcIceProperties;
        this.chatMessages = chatMessages;
        this.liveRooms = liveRooms;
        this.users = users;
        this.roomAccess = roomAccess;
    }

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session) {

        String clientId = UUID.randomUUID().toString();

        session.getAttributes().put(
                CLIENT_ID_ATTRIBUTE,
                clientId
        );

        clients.put(
                clientId,
                session
        );

        System.out.println(
                "CONNECTED: " + clientId
        );
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message) {

        try {

            JsonNode json =
                    objectMapper.readTree(
                            message.getPayload()
                    );

            String type =
                    json.get("type").asText();

            String clientId =
                    (String) session
                            .getAttributes()
                            .get(CLIENT_ID_ATTRIBUTE);

            System.out.println("[SIGNAL-IN] client=" + clientId
                    + " type=" + type
                    + " room=" + session.getAttributes().get(ROOM_ID_ATTRIBUTE)
                    + " target=" + json.path("targetId").asText("-"));

            if ("join".equals(type)) {

                handleJoin(
                        session,
                        clientId,
                        json
                );

                return;
            }

            String roomId =
                    (String) session
                            .getAttributes()
                            .get(ROOM_ID_ATTRIBUTE);

            if (roomId == null) {
                return;
            }

            // Chat is room-wide rather than peer-to-peer: one sender can talk to
            // the publisher and all viewers without knowing their client IDs.
            if ("chat".equals(type)) {
                String chatMessage = json.path("message").asText("").trim();
                if (chatMessage.isEmpty()) {
                    return;
                }
                if (chatMessage.length() > 800) {
                    sendError(session, "채팅은 800자 이내로 입력하세요.");
                    return;
                }
                persistChat(session, roomId, chatMessage);
                broadcastChat(roomId, clientId, message.getPayload());
                return;
            }

            String otherClientId = json.path("targetId").asText("");

            if (otherClientId.isBlank()) {
                return;
            }

            WebSocketSession other =
                    clients.get(otherClientId);

            if (other != null && roomId.equals(other.getAttributes().get(ROOM_ID_ATTRIBUTE)) &&
                    Set.of("offer", "answer", "candidate").contains(type) && other.isOpen()) {

                // 수신 측(특히 송신 앱)이 어떤 viewer의 응답인지 구별할 수 있게
                // 보낸 클라이언트 ID를 붙여 전달한다.
                other.sendMessage(new TextMessage(addPeerId(
                        message.getPayload(), clientId
                )));
                System.out.println("[SIGNAL-FORWARD] from=" + clientId
                        + " to=" + otherClientId + " type=" + type);
            } else {
                System.out.println("[SIGNAL-DROP] target unavailable: " + otherClientId
                        + " type=" + type);
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void persistChat(WebSocketSession session, String roomId, String content) {
        try {
            Long persistentRoomId = Long.valueOf(roomId);
            Long userId = (Long) session.getAttributes().get("userId");
            LiveRoom room = liveRooms.findById(persistentRoomId).orElseThrow();
            roomAccess.requireReadAccess(room, userId);
            User user = users.findById(userId).orElseThrow();
            chatMessages.save(new ChatMessage(room, user, content));
        } catch (NumberFormatException ignored) {
            // Legacy ephemeral room identifiers are intentionally not persisted.
        }
    }

    private void handleJoin(
            WebSocketSession session,
            String clientId,
            JsonNode json) {

        JsonNode roomIdNode = json.get("roomId");

        if (roomIdNode == null || roomIdNode.asText().isBlank()) {
            sendError(session, "roomId가 필요합니다.");
            return;
        }

        String roomId = roomIdNode.asText();

        String role = json.path("role").asText("");

        if (!role.isBlank()
                && !"publisher".equals(role)
                && !"viewer".equals(role)) {
            sendError(session, "role은 publisher 또는 viewer여야 합니다.");
            return;
        }

        if (session.getAttributes().containsKey(ROOM_ID_ATTRIBUTE)) {
            sendError(session, "이미 방에 입장했습니다."); return;
        }
        try {
            LiveRoom stored = liveRooms.findById(Long.valueOf(roomId)).orElseThrow();
            Long userId = (Long) session.getAttributes().get("userId");
            roomAccess.requireReadAccess(stored, userId);
            if (stored.getStatus() != com.bts.viewplustest.room.RoomStatus.LIVE ||
                ("publisher".equals(role) && !stored.getHost().getId().equals(userId)) ||
                (!"publisher".equals(role) && !"viewer".equals(role))) {
                sendError(session, "방 입장 권한 또는 상태를 확인하세요."); return;
            }
        } catch (RuntimeException invalid) {
            sendError(session, "방을 찾을 수 없거나 접근할 수 없습니다."); return;
        }

        Room room = rooms.computeIfAbsent(roomId, ignored -> new Room());

        synchronized (room) {
            room.removeClosedClients(clients);

            // 기존 앱(역할 필드 없음)은 기존 접속 순서를 유지한다. 새 앱과
            // 웹 뷰어는 항상 명시적 역할을 보내므로 접속 순서에 영향을 받지 않는다.
            if (role.isBlank()) {
                role = room.publisherId == null ? "publisher" : "viewer";
            }

            session.getAttributes().put(ROLE_ATTRIBUTE, role);

            System.out.println("[ROOM-JOIN] room=" + roomId + " client=" + clientId
                    + " role=" + role + " publisher=" + room.publisherId
                    + " viewers=" + room.viewerIds.size());

            if ("publisher".equals(role)) {
                if (room.publisherId != null) {
                    sendError(session, "이 방에는 이미 화면 송신자가 있습니다.");
                    return;
                }
                room.publisherId = clientId;
            } else {
                if (room.viewerIds.size() >= MAX_VIEWERS_PER_ROOM) {
                    sendError(session, "이 방의 최대 뷰어 수(3명)에 도달했습니다.");
                    return;
                }
                room.viewerIds.add(clientId);
            }

            session.getAttributes().put(ROOM_ID_ATTRIBUTE, roomId);
            connectRoomPeers(room, roomId, clientId, role);
        }
    }

    private String peerReadyMessage(boolean initiator, String peerId) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "type", "peer-ready",
                            "initiator", initiator,
                            "peerId", peerId,
                            "iceServers", webRtcIceProperties.toIceServers()
                    )
            );
        } catch (Exception e) {
            throw new IllegalStateException("ICE 서버 설정을 직렬화할 수 없습니다.", e);
        }
    }

    /**
     * publisher는 offer를 만드는 쪽, viewer는 수신/answer를 만드는 쪽으로 고정한다.
     * 접속 순서와 무관하게 화면 공유 흐름이 동일하게 유지된다.
     */
    private void connectRoomPeers(Room room, String roomId, String joinedClientId, String role) {
        if (room.publisherId == null || room.viewerIds.isEmpty()) {
            String waitingClientId = room.publisherId != null
                    ? room.publisherId
                    : room.viewerIds.stream().findFirst().orElse(null);
            send(clients.get(waitingClientId), "{\"type\":\"waiting\"}");
            System.out.println("WAITING: " + roomId);
            return;
        }

        WebSocketSession publisher = clients.get(room.publisherId);
        if (publisher == null || !publisher.isOpen()) {
            room.removeClosedClients(clients);
            return;
        }

        Set<String> viewerIds = "publisher".equals(role)
                ? room.viewerIds
                : Set.of(joinedClientId);
        for (String viewerId : viewerIds) {
            WebSocketSession viewer = clients.get(viewerId);
            if (viewer == null || !viewer.isOpen()) {
                continue;
            }
            send(publisher, peerReadyMessage(true, viewerId));
            send(viewer, peerReadyMessage(false, room.publisherId));
            System.out.println("[ROOM-PEER-READY] room=" + roomId
                    + " publisher=" + room.publisherId + " viewer=" + viewerId);
        }
        System.out.println("ROOM READY: " + roomId);
    }

    private String addPeerId(String payload, String peerId) {
        if (!payload.endsWith("}")) {
            return payload;
        }
        return payload.substring(0, payload.length() - 1)
                + ",\"peerId\":\"" + peerId + "\"}";
    }

    private void broadcastChat(String roomId, String senderId, String payload) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return;
        }
        String message = addPeerId(payload, senderId);
        for (String clientId : room.participantIds()) {
            if (!senderId.equals(clientId)) {
                send(clients.get(clientId), message);
            }
        }
        System.out.println("[CHAT] room=" + roomId + " sender=" + senderId);
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            send(session, objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "message", message
            )));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void send(WebSocketSession session, String message) {

        try {

            if (session != null &&
                    session.isOpen()) {

                session.sendMessage(
                        new TextMessage(message)
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            org.springframework.web.socket.CloseStatus status) {

        String clientId =
                (String) session
                        .getAttributes()
                            .get(CLIENT_ID_ATTRIBUTE);

        if (clientId != null) {

            clients.remove(clientId);

            String roomId =
                    (String) session
                            .getAttributes()
                            .get(ROOM_ID_ATTRIBUTE);

            if (roomId != null) {
                Room room = rooms.get(roomId);

                if (room != null) {
                    synchronized (room) {
                        Set<String> departingPeers = room.peerIdsFor(clientId);
                        if (clientId.equals(room.publisherId)) {
                            liveRooms.findById(Long.valueOf(roomId)).ifPresent(stored -> {
                                stored.end(); liveRooms.save(stored);
                            });
                        }
                        room.remove(clientId);
                        System.out.println("[ROOM-LEAVE] room=" + roomId
                                + " client=" + clientId + " status=" + status);

                        for (String peerId : departingPeers) {
                            WebSocketSession peer = clients.get(peerId);
                            if (peer != null && peer.isOpen()) {
                                send(peer, "{\"type\":\"peer-left\",\"peerId\":\""
                                        + clientId + "\"}");
                            }
                        }

                        if (room.isEmpty()) {
                            rooms.remove(roomId, room);
                        }
                    }
                }
            }
        }
    }

    public void closeBrowserSession(String sessionId) {
        for (WebSocketSession client : clients.values()) {
            if (sessionId.equals(client.getAttributes().get("httpSessionId"))) {
                try { client.close(org.springframework.web.socket.CloseStatus.NORMAL); }
                catch (java.io.IOException ignored) { }
            }
        }
    }

    private static class Room {

        private String publisherId;
        private final Set<String> viewerIds = ConcurrentHashMap.newKeySet();

        private void remove(String clientId) {
            if (clientId.equals(publisherId)) {
                publisherId = null;
            }
            viewerIds.remove(clientId);
        }

        private void removeClosedClients(
                Map<String, WebSocketSession> clients) {
            if (!isOpen(clients, publisherId)) {
                publisherId = null;
            }
            viewerIds.removeIf(clientId -> !isOpen(clients, clientId));
        }

        private boolean isEmpty() {
            return publisherId == null && viewerIds.isEmpty();
        }

        private Set<String> peerIdsFor(String clientId) {
            if (clientId.equals(publisherId)) {
                return Set.copyOf(viewerIds);
            }
            return publisherId == null ? Set.of() : Set.of(publisherId);
        }

        private Set<String> participantIds() {
            Set<String> ids = ConcurrentHashMap.newKeySet();
            if (publisherId != null) {
                ids.add(publisherId);
            }
            ids.addAll(viewerIds);
            return ids;
        }

        private boolean isOpen(
                Map<String, WebSocketSession> clients,
                String clientId) {
            if (clientId == null) {
                return false;
            }

            WebSocketSession client = clients.get(clientId);
            return client != null && client.isOpen();
        }
    }
}
