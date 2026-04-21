package com.gakkaweo.backend.config;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.system(KST);
  }
}
