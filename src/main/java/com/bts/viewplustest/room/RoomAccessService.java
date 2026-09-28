package com.bts.viewplustest.room;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class RoomAccessService {
    public void requireReadAccess(LiveRoom room, Long userId) {
        if (room.getVisibility() == RoomVisibility.PUBLIC || room.getHost().getId().equals(userId)) return;
        throw new AccessDeniedException("이 방의 채팅 기록에 접근할 권한이 없습니다.");
    }
}
