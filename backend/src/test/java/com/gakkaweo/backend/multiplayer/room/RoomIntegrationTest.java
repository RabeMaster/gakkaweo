package com.gakkaweo.backend.multiplayer.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.StompTestClient;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

@DisplayName("Room STOMP 통합 테스트")
class RoomIntegrationTest extends IntegrationTestBase {

  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("6인 방 생성 - 입장 - 준비 - 시작 - COUNTDOWN - PLAYING 전환 (마일스톤 1)")
  void 방_전체_흐름_마일스톤1() throws Exception {
    Member host = testAuthHelper.createMember();
    String hostCookie = testAuthHelper.accessTokenCookieFor(host);
    String wsUrl = "ws://localhost:" + port + "/ws";

    try (StompTestClient hostClient = new StompTestClient()) {
      StompSession hostSession = hostClient.connect(wsUrl, hostCookie);
      BlockingQueue<String> hostNotifications = new LinkedBlockingQueue<>();
      hostSession.subscribe("/user/queue/notifications", new StringFrameHandler(hostNotifications));

      hostSession.send(
          "/app/room/create", new CreateRoomPayload("테스트 방", "SENTENCE", 5, 120, 6, null, false));

      String createResponse = hostNotifications.poll(5, TimeUnit.SECONDS);
      assertThat(createResponse).isNotNull();
      JsonNode createNode = objectMapper.readTree(createResponse);
      assertThat(createNode.get("type").asText()).isEqualTo("ROOM_CREATED");
      String roomId = createNode.get("payload").get("roomId").asText();
      assertThat(roomId).hasSize(6);

      BlockingQueue<String> roomMessages = new LinkedBlockingQueue<>();
      hostSession.subscribe("/topic/room/" + roomId, new StringFrameHandler(roomMessages));
      BlockingQueue<String> gameMessages = new LinkedBlockingQueue<>();
      hostSession.subscribe(
          "/topic/room/" + roomId + "/game", new StringFrameHandler(gameMessages));

      List<StompTestClient> clients = new ArrayList<>();
      List<StompSession> sessions = new ArrayList<>();
      try {
        for (int i = 0; i < 5; i++) {
          Member player = testAuthHelper.createMember();
          String cookie = testAuthHelper.accessTokenCookieFor(player);
          StompTestClient client = new StompTestClient();
          clients.add(client);
          StompSession session = client.connect(wsUrl, cookie);
          sessions.add(session);

          BlockingQueue<String> playerNotifications = new LinkedBlockingQueue<>();
          session.subscribe(
              "/user/queue/notifications", new StringFrameHandler(playerNotifications));

          session.send("/app/room/" + roomId + "/join", new JoinPayload(null));

          String joinResponse = playerNotifications.poll(5, TimeUnit.SECONDS);
          assertThat(joinResponse).isNotNull();
          JsonNode joinNode = objectMapper.readTree(joinResponse);
          assertThat(joinNode.get("type").asText()).isEqualTo("ROOM_STATE");

          String joinBroadcast = roomMessages.poll(5, TimeUnit.SECONDS);
          assertThat(joinBroadcast).isNotNull();
        }

        for (StompSession session : sessions) {
          session.send("/app/room/" + roomId + "/ready", Map.of());
          String readyMsg = roomMessages.poll(5, TimeUnit.SECONDS);
          assertThat(readyMsg).isNotNull();
        }

        hostSession.send("/app/room/" + roomId + "/start", Map.of());

        String countdownMsg = gameMessages.poll(5, TimeUnit.SECONDS);
        assertThat(countdownMsg).isNotNull();
        JsonNode countdownNode = objectMapper.readTree(countdownMsg);
        assertThat(countdownNode.get("type").asText()).isEqualTo("COUNTDOWN");

        String gameStartedMsg = gameMessages.poll(10, TimeUnit.SECONDS);
        assertThat(gameStartedMsg).isNotNull();
        JsonNode gameStartedNode = objectMapper.readTree(gameStartedMsg);
        assertThat(gameStartedNode.get("type").asText()).isEqualTo("GAME_STARTED");
        assertThat(gameStartedNode.get("payload").get("status").asText()).isEqualTo("PLAYING");
      } finally {
        for (StompTestClient client : clients) {
          client.close();
        }
      }
    }
  }

  @Test
  @DisplayName("비밀번호 오류 시 입장이 거부된다")
  void 비밀번호_오류_입장_거부() throws Exception {
    Member host = testAuthHelper.createMember();
    String hostCookie = testAuthHelper.accessTokenCookieFor(host);
    String wsUrl = "ws://localhost:" + port + "/ws";

    try (StompTestClient hostClient = new StompTestClient()) {
      StompSession hostSession = hostClient.connect(wsUrl, hostCookie);
      BlockingQueue<String> hostNotifications = new LinkedBlockingQueue<>();
      hostSession.subscribe("/user/queue/notifications", new StringFrameHandler(hostNotifications));

      hostSession.send(
          "/app/room/create",
          new CreateRoomPayload("비번 방", "SENTENCE", 5, 120, 6, "secret123", false));

      String createResponse = hostNotifications.poll(5, TimeUnit.SECONDS);
      assertThat(createResponse).isNotNull();
      String roomId = objectMapper.readTree(createResponse).get("payload").get("roomId").asText();

      Member player = testAuthHelper.createMember();
      String playerCookie = testAuthHelper.accessTokenCookieFor(player);
      try (StompTestClient playerClient = new StompTestClient()) {
        StompSession playerSession = playerClient.connect(wsUrl, playerCookie);
        BlockingQueue<String> playerNotifications = new LinkedBlockingQueue<>();
        playerSession.subscribe(
            "/user/queue/notifications", new StringFrameHandler(playerNotifications));

        playerSession.send("/app/room/" + roomId + "/join", new JoinPayload("wrong"));

        String errorMsg = playerNotifications.poll(5, TimeUnit.SECONDS);
        assertThat(errorMsg).isNotNull();
        JsonNode errorNode = objectMapper.readTree(errorMsg);
        assertThat(errorNode.get("type").asText()).isEqualTo("ERROR");
      }
    }
  }

  @Test
  @DisplayName("강퇴당한 플레이어에게 KICKED 알림이 전송된다")
  void 강퇴_알림() throws Exception {
    Member host = testAuthHelper.createMember();
    Member player = testAuthHelper.createMember();
    String wsUrl = "ws://localhost:" + port + "/ws";

    try (StompTestClient hostClient = new StompTestClient();
        StompTestClient playerClient = new StompTestClient()) {
      StompSession hostSession =
          hostClient.connect(wsUrl, testAuthHelper.accessTokenCookieFor(host));
      StompSession playerSession =
          playerClient.connect(wsUrl, testAuthHelper.accessTokenCookieFor(player));

      BlockingQueue<String> hostNotifications = new LinkedBlockingQueue<>();
      hostSession.subscribe("/user/queue/notifications", new StringFrameHandler(hostNotifications));

      hostSession.send(
          "/app/room/create", new CreateRoomPayload("강퇴 테스트", "SENTENCE", 5, 120, 6, null, false));
      String createResponse = hostNotifications.poll(5, TimeUnit.SECONDS);
      String roomId = objectMapper.readTree(createResponse).get("payload").get("roomId").asText();

      BlockingQueue<String> playerNotifications = new LinkedBlockingQueue<>();
      playerSession.subscribe(
          "/user/queue/notifications", new StringFrameHandler(playerNotifications));

      playerSession.send("/app/room/" + roomId + "/join", new JoinPayload(null));
      playerNotifications.poll(5, TimeUnit.SECONDS);

      hostSession.send(
          "/app/room/" + roomId + "/kick",
          Map.of("targetPublicId", player.getPublicId().toString()));

      String kickedMsg = playerNotifications.poll(5, TimeUnit.SECONDS);
      assertThat(kickedMsg).isNotNull();
      JsonNode kickedNode = objectMapper.readTree(kickedMsg);
      assertThat(kickedNode.get("type").asText()).isEqualTo("KICKED");
    }
  }

  @Test
  @DisplayName("방장 퇴장 시 자동 위임이 broadcast된다")
  void 방장_퇴장_자동위임() throws Exception {
    Member host = testAuthHelper.createMember();
    Member player = testAuthHelper.createMember();
    String wsUrl = "ws://localhost:" + port + "/ws";

    try (StompTestClient hostClient = new StompTestClient();
        StompTestClient playerClient = new StompTestClient()) {
      StompSession hostSession =
          hostClient.connect(wsUrl, testAuthHelper.accessTokenCookieFor(host));
      StompSession playerSession =
          playerClient.connect(wsUrl, testAuthHelper.accessTokenCookieFor(player));

      BlockingQueue<String> hostNotifications = new LinkedBlockingQueue<>();
      hostSession.subscribe("/user/queue/notifications", new StringFrameHandler(hostNotifications));

      hostSession.send(
          "/app/room/create", new CreateRoomPayload("위임 테스트", "SENTENCE", 5, 120, 6, null, false));
      String createResponse = hostNotifications.poll(5, TimeUnit.SECONDS);
      String roomId = objectMapper.readTree(createResponse).get("payload").get("roomId").asText();

      BlockingQueue<String> playerNotifications = new LinkedBlockingQueue<>();
      playerSession.subscribe(
          "/user/queue/notifications", new StringFrameHandler(playerNotifications));
      BlockingQueue<String> roomMessages = new LinkedBlockingQueue<>();
      playerSession.subscribe("/topic/room/" + roomId, new StringFrameHandler(roomMessages));

      playerSession.send("/app/room/" + roomId + "/join", new JoinPayload(null));
      playerNotifications.poll(5, TimeUnit.SECONDS);
      roomMessages.poll(5, TimeUnit.SECONDS);

      hostSession.send("/app/room/" + roomId + "/leave", Map.of());

      String leaveMsg = roomMessages.poll(5, TimeUnit.SECONDS);
      assertThat(leaveMsg).isNotNull();
      JsonNode leaveNode = objectMapper.readTree(leaveMsg);
      assertThat(leaveNode.get("type").asText()).isEqualTo("PLAYER_LEFT");
      assertThat(leaveNode.get("payload").get("hostPublicId").asText())
          .isEqualTo(player.getPublicId().toString());
    }
  }

  private record CreateRoomPayload(
      String title,
      String mode,
      int rounds,
      int timeLimit,
      int maxPlayers,
      String password,
      boolean guessPublic) {}

  private record JoinPayload(String password) {}

  private record StringFrameHandler(BlockingQueue<String> queue) implements StompFrameHandler {

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return byte[].class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      queue.offer(new String((byte[]) payload));
    }
  }
}
