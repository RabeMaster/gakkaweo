package com.gakkaweo.backend.multiplayer.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Room 단위 테스트")
class RoomTest {

  private static final Instant NOW = Instant.parse("2026-05-19T00:00:00Z");

  private Room createDefaultRoom() {
    UUID hostId = UUID.randomUUID();
    RoomSettings settings = new RoomSettings("테스트 방", GameMode.SENTENCE, 5, 120, 6, null, false);
    Room room = new Room("ABC123", settings, hostId, NOW);
    room.addPlayer(hostId, "방장", NOW);
    return room;
  }

  @Test
  @DisplayName("플레이어를 추가하면 players에 반영된다")
  void 플레이어_추가() {
    Room room = createDefaultRoom();
    UUID playerId = UUID.randomUUID();
    room.addPlayer(playerId, "참가자1", NOW);

    assertThat(room.getPlayerCount()).isEqualTo(2);
    assertThat(room.getPlayers()).containsKey(playerId);
  }

  @Test
  @DisplayName("인원 초과 시 입장이 거부된다")
  void 인원초과_입장_거부() {
    UUID hostId = UUID.randomUUID();
    RoomSettings settings = new RoomSettings("테스트 방", GameMode.SENTENCE, 5, 120, 2, null, false);
    Room room = new Room("ABC123", settings, hostId, NOW);
    room.addPlayer(hostId, "방장", NOW);
    room.addPlayer(UUID.randomUUID(), "참가자1", NOW);

    assertThatThrownBy(() -> room.addPlayer(UUID.randomUUID(), "참가자2", NOW))
        .isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("WAITING이 아닌 방에는 입장할 수 없다")
  void waiting이_아닌_방_입장_거부() {
    Room room = createDefaultRoom();
    room.transitionToCountdown();

    assertThatThrownBy(() -> room.addPlayer(UUID.randomUUID(), "참가자1", NOW))
        .isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("플레이어를 제거하면 결과가 반환된다")
  void 플레이어_제거() {
    Room room = createDefaultRoom();
    UUID playerId = UUID.randomUUID();
    room.addPlayer(playerId, "참가자1", NOW);

    Room.RemoveResult result = room.removePlayer(playerId);
    assertThat(result.removed()).isTrue();
    assertThat(result.isEmpty()).isFalse();
    assertThat(room.getPlayerCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("방장이 퇴장하면 다음 플레이어에게 위임된다")
  void 방장_퇴장_자동위임() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();
    UUID player2 = UUID.randomUUID();
    UUID player3 = UUID.randomUUID();
    room.addPlayer(player2, "참가자1", NOW);
    room.addPlayer(player3, "참가자2", NOW);

    Room.RemoveResult result = room.removePlayer(hostId);
    assertThat(result.newHostPublicId()).isEqualTo(player2);
    assertThat(room.getHostPublicId()).isEqualTo(player2);
  }

  @Test
  @DisplayName("마지막 플레이어가 퇴장하면 ABANDONED된다")
  void 마지막_플레이어_퇴장_abandoned() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();

    Room.RemoveResult result = room.removePlayer(hostId);
    assertThat(result.isEmpty()).isTrue();
    assertThat(room.getStatus()).isEqualTo(RoomStatus.ABANDONED);
  }

  @Test
  @DisplayName("준비 상태를 토글할 수 있다")
  void 준비_토글() {
    Room room = createDefaultRoom();
    UUID playerId = UUID.randomUUID();
    room.addPlayer(playerId, "참가자1", NOW);

    boolean ready = room.toggleReady(playerId);
    assertThat(ready).isTrue();

    boolean notReady = room.toggleReady(playerId);
    assertThat(notReady).isFalse();
  }

  @Test
  @DisplayName("전원 준비 시 게임 시작 검증에 성공한다")
  void 게임시작_검증_성공() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();
    UUID playerId = UUID.randomUUID();
    room.addPlayer(playerId, "참가자1", NOW);
    room.toggleReady(playerId);

    room.validateAndPrepareStart(hostId);
  }

  @Test
  @DisplayName("방장이 아니면 게임 시작에 실패한다")
  void 게임시작_방장아님_실패() {
    Room room = createDefaultRoom();
    UUID playerId = UUID.randomUUID();
    room.addPlayer(playerId, "참가자1", NOW);
    room.toggleReady(playerId);

    assertThatThrownBy(() -> room.validateAndPrepareStart(playerId))
        .isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("준비하지 않은 플레이어가 있으면 시작에 실패한다")
  void 게임시작_미준비_실패() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();
    room.addPlayer(UUID.randomUUID(), "참가자1", NOW);

    assertThatThrownBy(() -> room.validateAndPrepareStart(hostId))
        .isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("혼자서는 게임을 시작할 수 없다")
  void 게임시작_1인_실패() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();

    assertThatThrownBy(() -> room.validateAndPrepareStart(hostId))
        .isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("방장만 설정을 변경할 수 있다")
  void 설정변경_방장만() {
    Room room = createDefaultRoom();
    UUID playerId = UUID.randomUUID();
    room.addPlayer(playerId, "참가자1", NOW);
    RoomSettings newSettings = new RoomSettings("새 제목", GameMode.WORD, 3, 60, 4, null, true);

    assertThatThrownBy(() -> room.updateSettings(playerId, newSettings))
        .isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("방장이 설정을 변경하면 반영된다")
  void 설정변경_성공() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();
    RoomSettings newSettings = new RoomSettings("새 제목", GameMode.WORD, 3, 60, 4, null, true);

    room.updateSettings(hostId, newSettings);
    assertThat(room.getSettings().title()).isEqualTo("새 제목");
    assertThat(room.getSettings().mode()).isEqualTo(GameMode.WORD);
  }

  @Test
  @DisplayName("현재 인원보다 적은 maxPlayers로 설정 변경이 거부된다")
  void 설정변경_인원초과_거부() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();
    room.addPlayer(UUID.randomUUID(), "참가자1", NOW);
    room.addPlayer(UUID.randomUUID(), "참가자2", NOW);
    RoomSettings newSettings = new RoomSettings("축소", GameMode.SENTENCE, 5, 120, 2, null, false);

    assertThatThrownBy(() -> room.updateSettings(hostId, newSettings))
        .isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("방장이 다른 플레이어를 강퇴할 수 있다")
  void 강퇴_성공() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();
    UUID targetId = UUID.randomUUID();
    room.addPlayer(targetId, "참가자1", NOW);

    room.kick(hostId, targetId);
    assertThat(room.getPlayerCount()).isEqualTo(1);
    assertThat(room.getPlayers()).doesNotContainKey(targetId);
  }

  @Test
  @DisplayName("자기 자신을 강퇴할 수 없다")
  void 강퇴_자기자신_실패() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();

    assertThatThrownBy(() -> room.kick(hostId, hostId)).isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("방장을 위임할 수 있다")
  void 방장위임_성공() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();
    UUID newHostId = UUID.randomUUID();
    room.addPlayer(newHostId, "새 방장", NOW);

    room.delegateHost(hostId, newHostId);
    assertThat(room.getHostPublicId()).isEqualTo(newHostId);
  }

  @Test
  @DisplayName("자기 자신에게 위임할 수 없다")
  void 방장위임_자기자신_실패() {
    Room room = createDefaultRoom();
    UUID hostId = room.getHostPublicId();

    assertThatThrownBy(() -> room.delegateHost(hostId, hostId)).isInstanceOf(RoomException.class);
  }

  @Test
  @DisplayName("비밀번호가 일치하면 checkPassword가 true를 반환한다")
  void 비밀번호_확인_성공() {
    UUID hostId = UUID.randomUUID();
    RoomSettings settings = new RoomSettings("비번 방", GameMode.SENTENCE, 5, 120, 6, "secret", false);
    Room room = new Room("ABC123", settings, hostId, NOW);

    assertThat(room.checkPassword("secret")).isTrue();
    assertThat(room.checkPassword("wrong")).isFalse();
  }

  @Test
  @DisplayName("비밀번호가 없으면 항상 통과한다")
  void 비밀번호_없는_방() {
    Room room = createDefaultRoom();

    assertThat(room.checkPassword(null)).isTrue();
    assertThat(room.checkPassword("anything")).isTrue();
  }

  @Test
  @DisplayName("revertToWaiting이 카운트다운을 취소하고 ready를 리셋한다")
  void revertToWaiting_리셋() {
    Room room = createDefaultRoom();
    UUID playerId = UUID.randomUUID();
    room.addPlayer(playerId, "참가자1", NOW);
    room.toggleReady(playerId);
    room.transitionToCountdown();

    room.revertToWaiting();

    assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
    assertThat(room.getPlayers().get(playerId).isReady()).isFalse();
  }
}
