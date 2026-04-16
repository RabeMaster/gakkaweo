package com.gakkaweo.backend.ratelimit.filter;

import org.springframework.stereotype.Component;

@Component
public class EndpointGroupResolver {

  public EndpointGroup resolve(String method, String uri) {
    if (uri.equals("/health")
        || uri.startsWith("/uploads/")
        || uri.startsWith("/login/oauth2/")
        || uri.startsWith("/oauth2/authorization/")
        || uri.startsWith("/swagger-ui")
        || uri.startsWith("/v3/api-docs")
        || uri.startsWith("/swagger-resources")
        || uri.startsWith("/webjars/")) {
      return EndpointGroup.NONE;
    }

    if (uri.startsWith("/admin/")) {
      return EndpointGroup.ADMIN;
    }

    if ("GET".equals(method) && uri.equals("/auth/me")) {
      return EndpointGroup.READ;
    }

    if (uri.startsWith("/auth")) {
      return EndpointGroup.AUTH;
    }

    if ("POST".equals(method) && uri.equals("/daily/guess")) {
      return EndpointGroup.GUESS;
    }

    if ("GET".equals(method) && uri.equals("/ranking/stream")) {
      return EndpointGroup.SSE;
    }

    if ("GET".equals(method)) {
      return EndpointGroup.READ;
    }

    return EndpointGroup.NONE;
  }
}
