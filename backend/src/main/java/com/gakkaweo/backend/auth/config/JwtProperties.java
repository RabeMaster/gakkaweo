package com.gakkaweo.backend.auth.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
@Getter
@RequiredArgsConstructor
public class JwtProperties {

  private final String accessSecret;
  private final Duration accessExpiration;
  private final Duration refreshExpiration;
}
