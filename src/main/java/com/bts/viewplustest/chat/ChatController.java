package com.bts.viewplustest.chat;

import com.bts.viewplustest.room.*;

import java.util.*;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@org.springframework.transaction.annotation.Transactional
@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
public class ChatController {
    private final ChatMessageRepository messages;
    private final LiveRoomRepository rooms;
    private final RoomAccessService access;

    public ChatController(ChatMessageRepository m, LiveRoomRepository r, RoomAccessService a) {
        messages = m;
        rooms = r;
        access = a;
    }

    @GetMapping
    public List<MessageResponse> history(Authentication a, @PathVariable Long roomId) {
        Long userId = (Long) a.getPrincipal();
        LiveRoom room = rooms.findById(roomId).orElseThrow(() -> new NoSuchElementException("방을 찾을 수 없습니다."));
        access.requireReadAccess(room, userId);
        return messages.findTop100ByRoomIdOrderByCreatedAtDesc(roomId).stream().map(m -> new MessageResponse(m.getId(), m.getSender().getId(), m.getSender().getNickname(), m.getContent(), m.getCreatedAt())).toList();
    }

    public record MessageResponse(Long id, Long senderUserId, String senderNickname, String content,
                                  java.time.Instant createdAt) {
    }
}
