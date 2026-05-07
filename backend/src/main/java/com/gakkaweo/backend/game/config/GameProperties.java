package com.gakkaweo.backend.game.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.game")
public record GameProperties(
    BigDecimal similarityThreshold,
    BigDecimal hintTriggerThreshold,
    BigDecimal hintMaxSimilarity,
    int hintMaxCount) {}
