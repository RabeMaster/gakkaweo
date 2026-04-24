package com.gakkaweo.backend.game.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.game")
@Getter
@RequiredArgsConstructor
public class GameProperties {

  private final BigDecimal similarityThreshold;
  private final BigDecimal hintTriggerThreshold;
  private final BigDecimal hintMaxSimilarity;
  private final int hintMaxCount;
}
