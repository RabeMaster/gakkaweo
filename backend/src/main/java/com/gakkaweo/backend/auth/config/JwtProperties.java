package com.gakkaweo.backend.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String accessSecret, Duration accessExpiration, Duration refreshExpiration) {}
