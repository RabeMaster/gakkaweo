package com.gakkaweo.backend.ranking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "랭킹 응답")
public record RankingResponse(
    @Schema(description = "랭킹 목록") List<RankingEntry> rankings,
    @Schema(description = "총 참여자 수", example = "42") long totalPlayers,
    @Schema(description = "내 랭킹 (비인증 시 null)", nullable = true) MyRank myRank,
    @Schema(description = "어제 내 최종 순위 (비인증 시 null)", nullable = true, example = "5")
        Integer yesterdayRank,
    @Schema(description = "어제 총 참여자 (비인증 시 null)", nullable = true, example = "38")
        Integer yesterdayTotalPlayers) {

  public RankingResponse(List<RankingEntry> rankings, long totalPlayers) {
    this(rankings, totalPlayers, null, null, null);
  }

  @Schema(description = "랭킹 항목")
  public record RankingEntry(
      @Schema(description = "순위", example = "1") long rank,
      @Schema(description = "회원 공개 ID", example = "550e8400-e29b-41d4-a716-446655440000")
          UUID publicId,
      @Schema(description = "닉네임", example = "가까워유저") String nickname,
      @Schema(description = "프로필 이미지 URL", nullable = true, example = "/uploads/profile.webp")
          String profileUrl,
      @Schema(description = "유사도", example = "95.2") BigDecimal similarity,
      @Schema(description = "시도 횟수", example = "8") int attemptCount) {}

  @Schema(description = "내 랭킹 정보")
  public record MyRank(
      @Schema(description = "순위", example = "3") long rank,
      @Schema(description = "유사도", example = "85.7") BigDecimal similarity,
      @Schema(description = "시도 횟수", example = "12") int attemptCount) {}
}
