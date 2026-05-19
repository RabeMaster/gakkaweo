package com.gakkaweo.backend.admin.controller;

import com.gakkaweo.backend.admin.dto.LobbySnapshotResponse;
import com.gakkaweo.backend.admin.dto.RoomDetailResponse;
import com.gakkaweo.backend.config.openapi.AdminErrorResponses;
import com.gakkaweo.backend.multiplayer.room.LobbyReadModel;
import com.gakkaweo.backend.multiplayer.room.Room;
import com.gakkaweo.backend.multiplayer.room.RoomManager;
import com.gakkaweo.backend.multiplayer.room.dto.LobbyRoomInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/multi")
@RequiredArgsConstructor
@Tag(name = "Admin: Multiplayer", description = "어드민 멀티플레이 모니터링")
@SecurityRequirement(name = "cookieAuth")
public class AdminMultiController {

  private final LobbyReadModel lobbyReadModel;
  private final RoomManager roomManager;

  @Operation(summary = "로비 스냅샷 조회")
  @AdminErrorResponses
  @GetMapping("/lobby/snapshot")
  public ResponseEntity<LobbySnapshotResponse> getLobbySnapshot() {
    List<LobbyRoomInfo> rooms = lobbyReadModel.getSnapshot();
    return ResponseEntity.ok(LobbySnapshotResponse.from(rooms, roomManager.getActivePlayerCount()));
  }

  @Operation(summary = "방 상세 조회")
  @AdminErrorResponses
  @GetMapping("/rooms/{roomId}")
  public ResponseEntity<RoomDetailResponse> getRoomDetail(@PathVariable String roomId) {
    Room room = roomManager.getRoom(roomId);
    if (room == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(RoomDetailResponse.from(room));
  }
}
