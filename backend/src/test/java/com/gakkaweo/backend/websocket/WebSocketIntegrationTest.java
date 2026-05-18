package com.gakkaweo.backend.websocket;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.StompTestClient;
import com.gakkaweo.backend.support.TestClock;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompSession;

class WebSocketIntegrationTest extends IntegrationTestBase {

  @Test
  @DisplayName("인증된 사용자의 WebSocket 핸드셰이크가 성공한다")
  void 인증사용자_핸드셰이크_성공() throws Exception {
    Member member = testAuthHelper.createMember();
    String cookie = testAuthHelper.accessTokenCookieFor(member);

    try (StompTestClient client = new StompTestClient()) {
      StompSession session = client.connect("ws://localhost:" + port + "/ws", cookie);
      assertThat(session.isConnected()).isTrue();
    }
  }

  @Test
  @DisplayName("미인증 요청의 WebSocket 핸드셰이크가 거부된다")
  void 미인증_핸드셰이크_거부() {
    try (StompTestClient client = new StompTestClient()) {
      assertThatThrownBy(() -> client.connect("ws://localhost:" + port + "/ws", ""))
          .isInstanceOf(Exception.class);
    }
  }

  @Test
  @DisplayName("만료된 토큰의 WebSocket 핸드셰이크가 거부된다")
  void 만료토큰_핸드셰이크_거부() {
    Member member = testAuthHelper.createMember();

    if (clock instanceof TestClock testClock) {
      testClock.setInstant(clock.instant().minus(Duration.ofHours(2)));
    }
    String cookie = testAuthHelper.accessTokenCookieFor(member);

    if (clock instanceof TestClock testClock) {
      testClock.setInstant(Clock.system(KST).instant());
    }

    try (StompTestClient client = new StompTestClient()) {
      assertThatThrownBy(() -> client.connect("ws://localhost:" + port + "/ws", cookie))
          .isInstanceOf(Exception.class);
    }
  }
}
