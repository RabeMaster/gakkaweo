package com.gakkaweo.backend.support;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

public class StompTestClient implements AutoCloseable {

  private final WebSocketStompClient stompClient;
  private StompSession session;

  public StompTestClient() {
    this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
  }

  public StompSession connect(String url, String cookieHeader) throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.add("Cookie", cookieHeader);

    CompletableFuture<StompSession> future =
        stompClient.connectAsync(
            url, headers, new StompHeaders(), new StompSessionHandlerAdapter() {});

    this.session = future.get(5, TimeUnit.SECONDS);
    return session;
  }

  public StompSession getSession() {
    return session;
  }

  @Override
  public void close() {
    if (session != null && session.isConnected()) {
      session.disconnect();
    }
    stompClient.stop();
  }
}
