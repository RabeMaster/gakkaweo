package com.gakkaweo.backend.config;

import com.gakkaweo.backend.auth.config.OAuth2Properties;
import com.gakkaweo.backend.auth.websocket.StompChannelInterceptor;
import com.gakkaweo.backend.auth.websocket.WebSocketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final MultiplayerProperties multiplayerProperties;
  private final OAuth2Properties oAuth2Properties;
  private final WebSocketHandshakeInterceptor handshakeInterceptor;
  private final StompChannelInterceptor channelInterceptor;
  private final ThreadPoolTaskScheduler stompHeartbeatScheduler;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    MultiplayerProperties.WebSocket ws = multiplayerProperties.webSocket();
    registry
        .enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[] {ws.heartbeatServer(), ws.heartbeatClient()})
        .setTaskScheduler(stompHeartbeatScheduler);
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws")
        .setAllowedOrigins(oAuth2Properties.authorizedRedirectUri())
        .addInterceptors(handshakeInterceptor);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    MultiplayerProperties.WebSocket ws = multiplayerProperties.webSocket();
    registry
        .setMessageSizeLimit(ws.messageSizeLimit())
        .setSendBufferSizeLimit(ws.sendBufferSizeLimit())
        .setSendTimeLimit(ws.sendTimeLimit());
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(channelInterceptor);
  }
}
