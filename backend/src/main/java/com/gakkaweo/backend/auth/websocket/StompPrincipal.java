package com.gakkaweo.backend.auth.websocket;

import java.security.Principal;
import java.util.UUID;

public record StompPrincipal(UUID publicId, String role) implements Principal {

  @Override
  public String getName() {
    return publicId.toString();
  }
}
