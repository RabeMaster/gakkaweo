package com.gakkaweo.backend.auth.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
@Getter
@RequiredArgsConstructor
public class ProfileImageProperties {

  private final String profileDir;
}
