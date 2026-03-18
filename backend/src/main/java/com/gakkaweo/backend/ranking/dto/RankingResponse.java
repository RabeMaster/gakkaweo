package com.gakkaweo.backend.ranking.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RankingResponse(List<RankingEntry> rankings, long totalPlayers) {

  public record RankingEntry(
      long rank, UUID publicId, String nickname, BigDecimal similarity, int attemptCount) {}
}
