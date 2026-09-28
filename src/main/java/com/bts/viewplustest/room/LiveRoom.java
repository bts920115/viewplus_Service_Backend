package com.bts.viewplustest.room;

import com.bts.viewplustest.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "live_rooms", indexes = @Index(name = "idx_live_rooms_status_started", columnList = "status,startedAt"))
public class LiveRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String title;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id")
    private User host;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RoomVisibility visibility;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RoomStatus status = RoomStatus.LIVE;
    @Column(nullable = false, updatable = false)
    private Instant startedAt = Instant.now();
    private Instant endedAt;

    protected LiveRoom() {
    }

    public LiveRoom(String title, User host, RoomVisibility visibility) {
        this.title = title;
        this.host = host;
        this.visibility = visibility;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public User getHost() {
        return host;
    }

    public RoomVisibility getVisibility() {
        return visibility;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void end() {
        status = RoomStatus.ENDED;
        endedAt = Instant.now();
    }
}
