package com.bts.viewplustest.room;

import com.bts.viewplustest.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.*;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@org.springframework.transaction.annotation.Transactional
@RestController
@RequestMapping("/api/rooms")
public class LiveRoomController {
    private final LiveRoomRepository rooms;
    private final UserRepository users;

    public LiveRoomController(LiveRoomRepository rooms, UserRepository users) {
        this.rooms = rooms;
        this.users = users;
    }

    @GetMapping
    public List<RoomResponse> list() {
        return rooms.findByStatusAndVisibilityOrderByStartedAtDesc(RoomStatus.LIVE, RoomVisibility.PUBLIC).stream().map(this::response).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(Authentication a, @Valid @RequestBody CreateRoomRequest r) {
        User user = users.findById(userId(a)).orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        return response(rooms.save(new LiveRoom(r.title().trim(), user, r.visibility())));
    }

    @PostMapping("/{id}/end")
    public RoomResponse end(Authentication a, @PathVariable Long id) {
        LiveRoom room = rooms.findById(id).orElseThrow(() -> new NoSuchElementException("방을 찾을 수 없습니다."));
        if (!room.getHost().getId().equals(userId(a)))
            throw new org.springframework.security.access.AccessDeniedException("방장만 종료할 수 있습니다.");
        room.end();
        return response(rooms.save(room));
    }

    private Long userId(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof Long id))
            throw new org.springframework.security.access.AccessDeniedException("로그인이 필요합니다.");
        return id;
    }

    private RoomResponse response(LiveRoom r) {
        return new RoomResponse(r.getId(), r.getTitle(), r.getHost().getId(), r.getHost().getNickname(), r.getVisibility(), r.getStatus(), r.getStartedAt());
    }

    public record CreateRoomRequest(@NotBlank @Size(max = 100) String title, @NotNull RoomVisibility visibility) {
    }

    public record RoomResponse(Long id, String title, Long hostUserId, String hostNickname, RoomVisibility visibility,
                               RoomStatus status, java.time.Instant startedAt) {
    }
}
