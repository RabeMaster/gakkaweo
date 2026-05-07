package com.gakkaweo.backend.infra.seeding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(String adminPassword) {}
