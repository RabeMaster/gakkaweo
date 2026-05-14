package com.gakkaweo.backend.auth.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthMetrics {

  private static final String[] PROVIDERS = {"local", "kakao", "google", "naver"};
  private static final Set<String> ALLOWED_PROVIDERS = Set.of(PROVIDERS);

  private final MeterRegistry meterRegistry;

  private static String sanitize(String provider) {
    return ALLOWED_PROVIDERS.contains(provider) ? provider : "unknown";
  }

  @PostConstruct
  void initCounters() {
    for (String provider : PROVIDERS) {
      Counter.builder("auth.login.total")
          .tag("provider", provider)
          .tag("result", "success")
          .description("Total login attempts")
          .register(meterRegistry);
      Counter.builder("auth.login.total")
          .tag("provider", provider)
          .tag("result", "failure")
          .description("Total login attempts")
          .register(meterRegistry);
      Counter.builder("auth.register.total")
          .tag("provider", provider)
          .description("Total registrations")
          .register(meterRegistry);
    }
    Counter.builder("auth.login.total")
        .tag("provider", "unknown")
        .tag("result", "success")
        .description("Total login attempts")
        .register(meterRegistry);
    Counter.builder("auth.login.total")
        .tag("provider", "unknown")
        .tag("result", "failure")
        .description("Total login attempts")
        .register(meterRegistry);
    Counter.builder("auth.register.total")
        .tag("provider", "unknown")
        .description("Total registrations")
        .register(meterRegistry);
    Counter.builder("auth.withdraw.total")
        .description("Total account withdrawals")
        .register(meterRegistry);
  }

  public void recordLogin(String provider, boolean success) {
    String safe = sanitize(provider);
    meterRegistry
        .counter("auth.login.total", "provider", safe, "result", success ? "success" : "failure")
        .increment();
  }

  public void recordRegister(String provider) {
    meterRegistry.counter("auth.register.total", "provider", sanitize(provider)).increment();
  }

  public void recordWithdraw() {
    meterRegistry.counter("auth.withdraw.total").increment();
  }
}
