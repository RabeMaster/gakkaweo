package com.gakkaweo.backend.infra.redis.scheduler;

public record RedisCleanupReport(
    int orphanCount,
    int purgedRankingCount,
    int purgedDetailCount,
    boolean orphanScanFailed,
    boolean rankingPurgeFailed) {

  public int totalPurgedCount() {
    return purgedRankingCount + purgedDetailCount;
  }

  public boolean hasAnyAction() {
    return orphanCount > 0 || totalPurgedCount() > 0;
  }

  public boolean hasFailure() {
    return orphanScanFailed || rankingPurgeFailed;
  }
}
