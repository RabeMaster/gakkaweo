package com.gakkaweo.backend.infra.seeding;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed")
@Getter
@RequiredArgsConstructor
public class SeedProperties {

  private final String adminPassword;
}
