package com.gakkaweo.backend.infra.monitoring;

import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.ranking.sse.SseConnectionManager;
import com.gakkaweo.backend.ratelimit.filter.BucketStore;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomMetricsConfig {

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
  MeterBinder unusedSentencesGauge(DailySentenceRepository dailySentenceRepository) {
    return registry ->
        Gauge.builder(
                "game.sentences.unused", dailySentenceRepository, repo -> repo.countUnusedActive())
            .description("Unused active sentences remaining")
            .register(registry);
  }
}
