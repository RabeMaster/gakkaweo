package com.gakkaweo.backend.config.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openapi")
public record OpenApiProperties(boolean docsMode) {}
