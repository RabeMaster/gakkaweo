package com.gakkaweo.backend.auth.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cookie")
@Getter
@RequiredArgsConstructor
public class CookieProperties {

  private final boolean secure;
  private final String domain;
}
