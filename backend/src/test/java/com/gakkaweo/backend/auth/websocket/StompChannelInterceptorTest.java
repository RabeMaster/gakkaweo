package com.gakkaweo.backend.auth.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.common.redis.RedisKeyConstants;
import com.gakkaweo.backend.ratelimit.filter.BucketStore;
import com.gakkaweo.backend.ratelimit.filter.EndpointGroup;
import com.gakkaweo.backend.ratelimit.filter.WsEndpointGroupResolver;
import io.github.bucket4j.Bucket;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

@DisplayName("StompChannelInterceptor 단위 테스트")
@ExtendWith(MockitoExtension.class)
class StompChannelInterceptorTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private BucketStore bucketStore;
  @Mock private WsEndpointGroupResolver wsEndpointGroupResolver;
  @Mock private Clock clock;
  @Mock private Bucket bucket;

  @InjectMocks private StompChannelInterceptor interceptor;

  @Test
  @DisplayName("CONNECT - Principal이 세션에 있으면 메시지가 통과한다")
  void connect_principal_설정() {
    StompPrincipal principal = new StompPrincipal(UUID.randomUUID(), "USER");
    Map<String, Object> sessionAttributes = new HashMap<>();
    sessionAttributes.put(WebSocketHandshakeInterceptor.ATTR_PRINCIPAL, principal);

    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setSessionAttributes(sessionAttributes);
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, null);

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("SEND - 만료 토큰이 거부된다")
  void send_만료토큰_거부() {
    long pastExpiry = Instant.now().minusSeconds(60).toEpochMilli();
    given(clock.millis()).willReturn(Instant.now().toEpochMilli());

    Message<?> message = buildSendMessage("/app/room/abc/ready", pastExpiry, null);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(MessageDeliveryException.class)
        .hasMessageContaining(ErrorCode.WS_TOKEN_EXPIRED.name());
  }

  @Test
  @DisplayName("SEND - blacklist 토큰이 거부된다")
  void send_blacklist_거부() {
    long futureExpiry = Instant.now().plusSeconds(3600).toEpochMilli();
    String jti = "test-jti";
    given(clock.millis()).willReturn(Instant.now().toEpochMilli());
    given(redisTemplate.hasKey(RedisKeyConstants.blacklistKey(jti))).willReturn(true);

    Message<?> message = buildSendMessage("/app/room/abc/ready", futureExpiry, jti);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(MessageDeliveryException.class)
        .hasMessageContaining(ErrorCode.WS_TOKEN_REVOKED.name());
  }

  @Test
  @DisplayName("SEND - rate limit 초과 시 거부된다")
  void send_rateLimit_거부() {
    long futureExpiry = Instant.now().plusSeconds(3600).toEpochMilli();
    String jti = "test-jti";
    given(clock.millis()).willReturn(Instant.now().toEpochMilli());
    given(redisTemplate.hasKey(RedisKeyConstants.blacklistKey(jti))).willReturn(false);
    given(wsEndpointGroupResolver.resolve("/app/room/abc/ready"))
        .willReturn(EndpointGroup.ROOM_ACTION);
    given(
            bucketStore.resolveBucket(
                EndpointGroup.ROOM_ACTION, "00000000-0000-0000-0000-000000000001"))
        .willReturn(bucket);
    given(bucket.tryConsume(1)).willReturn(false);

    StompPrincipal principal =
        new StompPrincipal(UUID.fromString("00000000-0000-0000-0000-000000000001"), "USER");
    Message<?> message =
        buildSendMessageWithPrincipal("/app/room/abc/ready", futureExpiry, jti, principal);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(MessageDeliveryException.class)
        .hasMessageContaining(ErrorCode.WS_RATE_LIMIT_EXCEEDED.name());
  }

  @Test
  @DisplayName("SUBSCRIBE - 만료 토큰이 거부된다")
  void subscribe_만료토큰_거부() {
    long pastExpiry = Instant.now().minusSeconds(60).toEpochMilli();
    given(clock.millis()).willReturn(Instant.now().toEpochMilli());

    Message<?> message = buildSubscribeMessage("/topic/room/abc", pastExpiry, null);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(MessageDeliveryException.class)
        .hasMessageContaining(ErrorCode.WS_TOKEN_EXPIRED.name());
  }

  @Test
  @DisplayName("command가 null이면 메시지를 그대로 통과시킨다")
  void command_null_통과() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setSessionAttributes(new HashMap<>());
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    StompHeaderAccessor nullAccessor = StompHeaderAccessor.wrap(message);
    nullAccessor.setHeader("stompCommand", null);

    Message<?> result = interceptor.preSend(message, null);
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("sessionAttributes가 null이면 메시지를 그대로 통과시킨다")
  void sessionAttributes_null_통과() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, null);
    assertThat(result).isNotNull();
  }

  private Message<?> buildSendMessage(String destination, long expiresAt, String jti) {
    return buildMessage(StompCommand.SEND, destination, expiresAt, jti, null);
  }

  private Message<?> buildSendMessageWithPrincipal(
      String destination, long expiresAt, String jti, StompPrincipal principal) {
    return buildMessage(StompCommand.SEND, destination, expiresAt, jti, principal);
  }

  private Message<?> buildSubscribeMessage(String destination, long expiresAt, String jti) {
    return buildMessage(StompCommand.SUBSCRIBE, destination, expiresAt, jti, null);
  }

  private Message<?> buildMessage(
      StompCommand command,
      String destination,
      long expiresAt,
      String jti,
      StompPrincipal principal) {
    Map<String, Object> sessionAttributes = new HashMap<>();
    sessionAttributes.put(WebSocketHandshakeInterceptor.ATTR_EXPIRES_AT, expiresAt);
    if (jti != null) {
      sessionAttributes.put(WebSocketHandshakeInterceptor.ATTR_JTI, jti);
    }

    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setSessionAttributes(sessionAttributes);
    accessor.setDestination(destination);
    if (principal != null) {
      accessor.setUser(principal);
    }
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
