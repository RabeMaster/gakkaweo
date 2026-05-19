package com.gakkaweo.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.ratelimit.filter.EndpointGroup;
import com.gakkaweo.backend.ratelimit.filter.WsEndpointGroupResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WsEndpointGroupResolver 단위 테스트")
class WsEndpointGroupResolverTest {

  private final WsEndpointGroupResolver resolver = new WsEndpointGroupResolver();

  @Test
  @DisplayName("GUESS_WS - /app/room/{roomId}/guess")
  void guessWs_매핑() {
    assertThat(resolver.resolve("/app/room/abc123/guess")).isEqualTo(EndpointGroup.GUESS_WS);
  }

  @Test
  @DisplayName("CHAT_WS - /app/room/{roomId}/chat")
  void chatWs_매핑() {
    assertThat(resolver.resolve("/app/room/abc123/chat")).isEqualTo(EndpointGroup.CHAT_WS);
  }

  @Test
  @DisplayName("INVITE_WS - /app/friend/invite")
  void inviteWs_매핑() {
    assertThat(resolver.resolve("/app/friend/invite")).isEqualTo(EndpointGroup.INVITE_WS);
    assertThat(resolver.resolve("/app/friend/invite/abc")).isEqualTo(EndpointGroup.INVITE_WS);
  }

  @Test
  @DisplayName("ROOM_ACTION - /app/room/** (guess/chat 제외)")
  void roomAction_매핑() {
    assertThat(resolver.resolve("/app/room/abc123/ready")).isEqualTo(EndpointGroup.ROOM_ACTION);
    assertThat(resolver.resolve("/app/room/abc123/start")).isEqualTo(EndpointGroup.ROOM_ACTION);
    assertThat(resolver.resolve("/app/room/create")).isEqualTo(EndpointGroup.ROOM_ACTION);
  }

  @Test
  @DisplayName("null - 미매핑 destination")
  void null_미매핑() {
    assertThat(resolver.resolve("/app/other/something")).isNull();
    assertThat(resolver.resolve("/topic/lobby")).isNull();
  }

  @Test
  @DisplayName("null - destination이 null")
  void null_destination() {
    assertThat(resolver.resolve(null)).isNull();
  }
}
