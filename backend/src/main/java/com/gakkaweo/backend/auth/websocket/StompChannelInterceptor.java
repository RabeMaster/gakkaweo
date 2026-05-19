package com.gakkaweo.backend.auth.websocket;

import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.common.redis.RedisKeyConstants;
import com.gakkaweo.backend.ratelimit.filter.BucketStore;
import com.gakkaweo.backend.ratelimit.filter.EndpointGroup;
import com.gakkaweo.backend.ratelimit.filter.WsEndpointGroupResolver;
import io.github.bucket4j.Bucket;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

  private final StringRedisTemplate redisTemplate;
  private final BucketStore bucketStore;
  private final WsEndpointGroupResolver wsEndpointGroupResolver;
  private final Clock clock;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    StompCommand command = accessor.getCommand();

    if (command == null) {
      return message;
    }

    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    if (sessionAttributes == null) {
      return message;
    }

    if (command == StompCommand.CONNECT) {
      StompPrincipal principal =
          (StompPrincipal) sessionAttributes.get(WebSocketHandshakeInterceptor.ATTR_PRINCIPAL);
      if (principal != null) {
        accessor.setUser(principal);
      }
      return message;
    }

    if (command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) {
      Long expiresAt = (Long) sessionAttributes.get(WebSocketHandshakeInterceptor.ATTR_EXPIRES_AT);
      if (expiresAt != null && clock.millis() > expiresAt) {
        throw new MessageDeliveryException(ErrorCode.WS_TOKEN_EXPIRED.name());
      }

      String jti = (String) sessionAttributes.get(WebSocketHandshakeInterceptor.ATTR_JTI);
      if (jti != null && redisTemplate.hasKey(RedisKeyConstants.blacklistKey(jti))) {
        throw new MessageDeliveryException(ErrorCode.WS_TOKEN_REVOKED.name());
      }

      if (command == StompCommand.SEND) {
        String destination = accessor.getDestination();
        EndpointGroup group = wsEndpointGroupResolver.resolve(destination);
        if (group != null) {
          java.security.Principal user = accessor.getUser();
          String key = user != null ? user.getName() : "anonymous";
          Bucket bucket = bucketStore.resolveBucket(group, key);
          if (!bucket.tryConsume(1)) {
            throw new MessageDeliveryException(ErrorCode.WS_RATE_LIMIT_EXCEEDED.name());
          }
        }
      }
    }

    return message;
  }
}
