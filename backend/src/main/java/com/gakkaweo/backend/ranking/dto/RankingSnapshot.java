package com.gakkaweo.backend.ranking.dto;

import java.util.List;
import java.util.UUID;

public record RankingSnapshot(List<MemberRank> memberRanks, int totalPlayers) {

  public record MemberRank(UUID publicId, int rank) {}
}
