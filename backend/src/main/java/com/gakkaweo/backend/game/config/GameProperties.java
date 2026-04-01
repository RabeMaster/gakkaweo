package com.gakkaweo.backend.game.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.game")
@Getter
@Setter
public class GameProperties {

  private BigDecimal similarityThreshold = new BigDecimal("95.0");
  private BigDecimal hintTriggerThreshold = new BigDecimal("60.0");
  private BigDecimal hintMaxSimilarity = new BigDecimal("90.0");
  private int hintMaxCount = 5;
}
