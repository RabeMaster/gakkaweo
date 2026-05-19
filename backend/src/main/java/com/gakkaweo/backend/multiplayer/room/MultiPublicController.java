package com.gakkaweo.backend.multiplayer.room;

import com.gakkaweo.backend.multiplayer.room.dto.LobbyRoomInfo;
import com.gakkaweo.backend.multiplayer.room.dto.RoomSnapshot;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/multi")
@RequiredArgsConstructor
public class MultiPublicController {

  private final LobbyReadModel lobbyReadModel;
  private final RoomManager roomManager;

  @GetMapping("/lobby")
  public List<LobbyRoomInfo> getLobby() {
    return lobbyReadModel.getSnapshot();
  }

  @GetMapping("/rooms/{roomId}")
  public ResponseEntity<RoomSnapshot> getRoom(@PathVariable String roomId) {
    Room room = roomManager.getRoom(roomId);
    if (room == null) {
      return ResponseEntity.notFound().build();
    }
    room.getLock().lock();
    try {
      return ResponseEntity.ok(RoomSnapshot.from(room));
    } finally {
      room.getLock().unlock();
    }
  }
}
