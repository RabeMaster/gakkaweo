package com.gakkaweo.backend.config;

import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@RequiredArgsConstructor
public class AuditConfig {

  private final Clock clock;

  @Bean
  public DateTimeProvider auditingDateTimeProvider() {
    return () -> Optional.of(clock.instant());
  }
}
