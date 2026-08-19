package com.gakkaweo.backend.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
public record ProfileImageProperties(String profileDir) {}
