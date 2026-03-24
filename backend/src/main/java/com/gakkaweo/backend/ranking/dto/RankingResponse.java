package com.gakkaweo.backend.ranking.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RankingResponse(
    List<RankingEntry> rankings,
    long totalPlayers,
    MyRank myRank,
    Integer yesterdayRank,
    Integer yesterdayTotalPlayers) {

  public RankingResponse(List<RankingEntry> rankings, long totalPlayers) {
    this(rankings, totalPlayers, null, null, null);
  }

  public record RankingEntry(
      long rank,
      UUID publicId,
      String nickname,
      String profileUrl,
      BigDecimal similarity,
      int attemptCount) {}

  public record MyRank(long rank, BigDecimal similarity, int attemptCount) {}
}
