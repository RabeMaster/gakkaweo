package com.gakkaweo.backend.config.openapi;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openapi")
@Getter
@RequiredArgsConstructor
public class OpenApiProperties {

  private final boolean docsMode;
}
