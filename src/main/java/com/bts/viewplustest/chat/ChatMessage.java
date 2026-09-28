package com.bts.viewplustest.chat;

import com.bts.viewplustest.room.LiveRoom;
import com.bts.viewplustest.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_room_created", columnList = "room_id,createdAt"))
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private LiveRoom room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_user_id")
    private User sender;
    @Column(nullable = false, length = 800)
    private String content;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ChatMessage() {
    }

    public ChatMessage(LiveRoom room, User sender, String content) {
        this.room = room;
        this.sender = sender;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public User getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
