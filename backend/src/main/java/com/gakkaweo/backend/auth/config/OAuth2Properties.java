package com.gakkaweo.backend.auth.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth2")
@Getter
@RequiredArgsConstructor
public class OAuth2Properties {

  private final String authorizedRedirectUri;
}
