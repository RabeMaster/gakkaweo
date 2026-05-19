package com.gakkaweo.backend.ratelimit.filter;

import org.springframework.stereotype.Component;

@Component
public class WsEndpointGroupResolver {

  public EndpointGroup resolve(String destination) {
    if (destination == null) {
      return null;
    }
    if (destination.matches("/app/room/[^/]+/guess")) {
      return EndpointGroup.GUESS_WS;
    }
    if (destination.matches("/app/room/[^/]+/chat")) {
      return EndpointGroup.CHAT_WS;
    }
    if (destination.startsWith("/app/friend/invite")) {
      return EndpointGroup.INVITE_WS;
    }
    if (destination.matches("/app/room/.*")) {
      return EndpointGroup.ROOM_ACTION;
    }
    return null;
  }
}
