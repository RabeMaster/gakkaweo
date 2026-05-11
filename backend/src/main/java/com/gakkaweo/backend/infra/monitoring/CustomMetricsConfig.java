package com.gakkaweo.backend.infra.monitoring;

import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.ranking.sse.SseConnectionManager;
import com.gakkaweo.backend.ratelimit.filter.BucketStore;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@RequiredArgsConstructor
public class CustomMetricsConfig {

  private final DailySentenceRepository dailySentenceRepository;
  private final AtomicLong unusedSentenceCount = new AtomicLong();

  @Scheduled(fixedRate = 300_000)
  void refreshUnusedSentenceCount() {
    unusedSentenceCount.set(dailySentenceRepository.countUnusedActive());
  }

  @Bean
  TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }

  @Bean
  MeterBinder sseConnectionsGauge(SseConnectionManager sseConnectionManager) {
    return registry ->
        Gauge.builder(
                "sse.connections", sseConnectionManager, SseConnectionManager::getConnectionCount)
            .description("Active SSE connections")
            .register(registry);
  }

  @Bean
  MeterBinder rateLimitBucketsGauge(BucketStore bucketStore) {
    return registry ->
        Gauge.builder("ratelimit.buckets.active", bucketStore, BucketStore::getBucketCount)
            .description("Active rate limit buckets")
            .register(registry);
  }

  @Bean
  MeterBinder unusedSentencesGauge() {
    return registry ->
        Gauge.builder("game.sentences.unused", unusedSentenceCount, AtomicLong::doubleValue)
            .description("Unused active sentences remaining")
            .register(registry);
  }
}
