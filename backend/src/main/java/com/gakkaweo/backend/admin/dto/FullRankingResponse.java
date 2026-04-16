package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "전체 랭킹 응답")
public record FullRankingResponse(
    @Schema(description = "랭킹 목록") List<RankingEntry> rankings,
    @Schema(description = "총 참여자 수", example = "42") long totalPlayers) {

  @Schema(description = "랭킹 항목")
  public record RankingEntry(
      @Schema(description = "순위", example = "1") long rank,
      @Schema(description = "회원 공개 ID", example = "550e8400-e29b-41d4-a716-446655440000")
          UUID publicId,
      @Schema(description = "닉네임", example = "가까워유저") String nickname,
      @Schema(description = "프로필 이미지 URL", nullable = true, example = "/uploads/profile.webp")
          String profileUrl,
      @Schema(description = "유사도", example = "98.72") BigDecimal similarity,
      @Schema(description = "시도 횟수", example = "5") int attemptCount) {

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
