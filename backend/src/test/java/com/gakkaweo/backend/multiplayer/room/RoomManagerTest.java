package com.gakkaweo.backend.multiplayer.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.config.MultiplayerProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RoomManager 단위 테스트")
class RoomManagerTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), ZoneOffset.UTC);

  private RoomManager roomManager;

  @BeforeEach
  void setUp() {
    MultiplayerProperties properties =
        new MultiplayerProperties(
            new MultiplayerProperties.Timer(4),
            new MultiplayerProperties.WebSocket(8192, 524288, 20000, 25000, 25000),
            new MultiplayerProperties.Room(10, 5));
    roomManager = new RoomManager(properties);
  }

  private RoomSettings defaultSettings() {
    return new RoomSettings("테스트 방", GameMode.SENTENCE, 5, 120, 6, null, false);
  }

  @Test
  @DisplayName("방을 생성하면 ID가 부여되고 맵에 등록된다")
  void 방_생성_성공() {
    UUID hostId = UUID.randomUUID();
    Room room = roomManager.createRoom(defaultSettings(), hostId, "방장", FIXED_CLOCK);

    assertThat(room.getId()).hasSize(6);
    assertThat(roomManager.getRoom(room.getId())).isNotNull();
    assertThat(roomManager.getActiveRoomId(hostId)).isEqualTo(room.getId());
  }

  @Test
  @DisplayName("동시 방 상한 초과 시 생성이 거부된다")
  void 방_상한_초과() {
    MultiplayerProperties properties =
        new MultiplayerProperties(
            new MultiplayerProperties.Timer(4),
            new MultiplayerProperties.WebSocket(8192, 524288, 20000, 25000, 25000),
            new MultiplayerProperties.Room(2, 5));
    RoomManager limitedManager = new RoomManager(properties);

    limitedManager.createRoom(defaultSettings(), UUID.randomUUID(), "방장1", FIXED_CLOCK);
    limitedManager.createRoom(defaultSettings(), UUID.randomUUID(), "방장2", FIXED_CLOCK);

    assertThatThrownBy(
            () ->
                limitedManager.createRoom(defaultSettings(), UUID.randomUUID(), "방장3", FIXED_CLOCK))
        .isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("이미 방에 참가 중인 사용자는 새 방을 만들 수 없다")
  void 이미_참가중_생성_거부() {
    UUID hostId = UUID.randomUUID();
    roomManager.createRoom(defaultSettings(), hostId, "방장", FIXED_CLOCK);

    assertThatThrownBy(() -> roomManager.createRoom(defaultSettings(), hostId, "방장", FIXED_CLOCK))
        .isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("멤버십 등록은 1인 1방을 강제한다")
  void 멤버십_1인1방() {
    UUID playerId = UUID.randomUUID();

    boolean first = roomManager.registerMembership(playerId, "ROOM_A");
    boolean second = roomManager.registerMembership(playerId, "ROOM_B");

    assertThat(first).isTrue();
    assertThat(second).isFalse();
    assertThat(roomManager.getActiveRoomId(playerId)).isEqualTo("ROOM_A");
  }

  @Test
  @DisplayName("멤버십 해제 후 다른 방에 참가할 수 있다")
  void 멤버십_해제_후_재참가() {
    UUID playerId = UUID.randomUUID();
    roomManager.registerMembership(playerId, "ROOM_A");
    roomManager.unregisterMembership(playerId);

    boolean result = roomManager.registerMembership(playerId, "ROOM_B");
    assertThat(result).isTrue();
    assertThat(roomManager.getActiveRoomId(playerId)).isEqualTo("ROOM_B");
  }

  @Test
  @DisplayName("빠른 입장은 WAITING + 비밀번호 없음 + 빈 자리 조건을 만족하는 방을 찾는다")
  void 빠른_입장_조건() {
    UUID hostId = UUID.randomUUID();
    Room room = roomManager.createRoom(defaultSettings(), hostId, "방장", FIXED_CLOCK);

    Room found = roomManager.findQuickJoinRoom();
    assertThat(found).isNotNull();
    assertThat(found.getId()).isEqualTo(room.getId());
  }

  @Test
  @DisplayName("비밀번호가 있는 방은 빠른 입장 후보에서 제외된다")
  void 빠른_입장_비밀번호_제외() {
    RoomSettings withPassword =
        new RoomSettings("비번 방", GameMode.SENTENCE, 5, 120, 6, "pass", false);
    roomManager.createRoom(withPassword, UUID.randomUUID(), "방장", FIXED_CLOCK);

    Room found = roomManager.findQuickJoinRoom();
    assertThat(found).isNull();
  }

  @Test
  @DisplayName("clearAll이 모든 상태를 초기화한다")
  void clearAll_초기화() {
    UUID hostId = UUID.randomUUID();
    roomManager.createRoom(defaultSettings(), hostId, "방장", FIXED_CLOCK);

    roomManager.clearAll();

    assertThat(roomManager.getRoomCount()).isZero();
    assertThat(roomManager.getActivePlayerCount()).isZero();
    assertThat(roomManager.getActiveRoomId(hostId)).isNull();
  }
}
