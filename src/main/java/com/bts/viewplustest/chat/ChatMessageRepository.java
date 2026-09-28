package com.bts.viewplustest.chat;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop100ByRoomIdOrderByCreatedAtDesc(Long roomId);
}
