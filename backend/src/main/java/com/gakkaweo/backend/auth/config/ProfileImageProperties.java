package com.gakkaweo.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
public record ProfileImageProperties(String profileDir) {}
