package com.gakkaweo.backend.multiplayer.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.gakkaweo.backend.multiplayer.room.dto.LobbyRoomInfo;
import com.gakkaweo.backend.multiplayer.room.dto.RoomCreateRequest;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("LobbyReadModel 동시성 통합 테스트")
class LobbyReadModelIntegrationTest extends IntegrationTestBase {

  @Autowired private RoomService roomService;
  @Autowired private LobbyReadModel lobbyReadModel;

  @Test
  @DisplayName("CompletableFuture 6명 동시 멤버십 등록/해제 후 최종 상태 일관성 (마일스톤 2)")
  void 동시_멤버십_등록해제_일관성() {
    List<UUID> playerIds = IntStream.range(0, 6).mapToObj(i -> UUID.randomUUID()).toList();

    CompletableFuture<?>[] futures =
        playerIds.stream()
            .map(
                pid ->
                    CompletableFuture.runAsync(
                        () -> {
                          try {
                            roomManager.registerMembership(pid, "ROOM_" + pid);
                            Thread.sleep(10);
                            roomManager.unregisterMembership(pid);
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                          }
                        }))
            .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(futures).join();

    assertThat(roomManager.getActivePlayerCount()).isZero();
  }

  @Test
  @DisplayName("방 생성/삭제 이벤트 후 스냅샷이 갱신된다")
  void 이벤트_기반_스냅샷_갱신() {
    var host = testAuthHelper.createMember();
    var request = new RoomCreateRequest("스냅샷 테스트", GameMode.SENTENCE, 5, 120, 6, null, false);
    var snapshot = roomService.createRoom(request, host.getPublicId(), host.getNickname());
    String roomId = snapshot.roomId();

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              List<LobbyRoomInfo> lobbySnapshot = lobbyReadModel.getSnapshot();
              assertThat(lobbySnapshot).anyMatch(r -> r.roomId().equals(roomId));
            });

    roomService.leaveRoom(roomId, host.getPublicId());

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              List<LobbyRoomInfo> lobbySnapshot = lobbyReadModel.getSnapshot();
              assertThat(lobbySnapshot).noneMatch(r -> r.roomId().equals(roomId));
            });
  }
}
