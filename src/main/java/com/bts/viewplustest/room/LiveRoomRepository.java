package com.bts.viewplustest.room;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveRoomRepository extends JpaRepository<LiveRoom, Long> {
    List<LiveRoom> findByStatusAndVisibilityOrderByStartedAtDesc(RoomStatus status, RoomVisibility visibility);
}
