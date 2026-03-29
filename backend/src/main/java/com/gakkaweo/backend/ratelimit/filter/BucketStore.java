package com.gakkaweo.backend.ratelimit.filter;

import com.gakkaweo.backend.ratelimit.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BucketStore {

  private final RateLimitProperties properties;
  private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();

  public Bucket resolveBucket(EndpointGroup group, String key) {
    String bucketKey = group.name() + ":" + key;
    return buckets
        .compute(
            bucketKey,
            (k, existing) -> {
              if (existing != null) {
                return new BucketEntry(existing.bucket(), Instant.now());
              }
              Bucket bucket = createBucket(group);
              return new BucketEntry(bucket, Instant.now());
            })
        .bucket();
  }

  @Scheduled(fixedDelayString = "${app.rate-limit.cleanup-interval-ms:300000}")
  public void cleanup() {
    Instant expiry = Instant.now().minusSeconds(properties.getBucketExpiryMinutes() * 60L);
    int before = buckets.size();
    buckets.entrySet().removeIf(entry -> entry.getValue().lastAccessed().isBefore(expiry));
    int removed = before - buckets.size();
    if (removed > 0) {
      log.debug("Rate limit 버킷 정리: {}개 제거, 남은 버킷 {}개", removed, buckets.size());
    }
  }

  private Bucket createBucket(EndpointGroup group) {
    int capacity = getCapacity(group);
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofMinutes(1))
                .build())
        .build();
  }

  private int getCapacity(EndpointGroup group) {
    return switch (group) {
      case GUESS -> properties.getGuessPerMinute();
      case READ -> properties.getReadPerMinute();
      case SSE -> properties.getSsePerMinute();
      case AUTH -> properties.getAuthPerMinute();
      case ADMIN -> properties.getAdminPerMinute();
      case NONE -> throw new IllegalArgumentException("NONE 그룹은 버킷을 생성하지 않습니다");
    };
  }

  private record BucketEntry(Bucket bucket, Instant lastAccessed) {}
}
