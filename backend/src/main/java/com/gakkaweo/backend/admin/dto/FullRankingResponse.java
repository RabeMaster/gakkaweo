package com.gakkaweo.backend.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FullRankingResponse(List<RankingEntry> rankings, long totalPlayers) {

  public record RankingEntry(
      long rank,
      UUID publicId,
      String nickname,
      String profileUrl,
      BigDecimal similarity,
      int attemptCount) {

    public static RankingEntry from(
        com.gakkaweo.backend.ranking.dto.RankingResponse.RankingEntry entry) {
      return new RankingEntry(
          entry.rank(),
          entry.publicId(),
          entry.nickname(),
          entry.profileUrl(),
          entry.similarity(),
          entry.attemptCount());
    }
  }
}
