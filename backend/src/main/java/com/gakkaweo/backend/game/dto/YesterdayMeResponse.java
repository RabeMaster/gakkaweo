package com.gakkaweo.backend.game.dto;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "어제 내 게임 기록 응답")
public record YesterdayMeResponse(
    @Schema(description = "어제 날짜 (어제 문장 없으면 null)", nullable = true, example = "2026-09-05")
        LocalDate yesterdayDate,
    @Schema(description = "어제 참여 여부 (false면 아래 필드 전부 null)", example = "true") boolean participated,
    @Schema(description = "어제 최종 순위 (미확정 시 null)", nullable = true, example = "3") Integer rank,
    @Schema(description = "어제 총 참여자 수", nullable = true, example = "12") Integer totalPlayers,
    @Schema(description = "최고 유사도 추측 문장", nullable = true, example = "고양이가 창가에서 잠을 잔다")
        String bestGuessText,
    @Schema(description = "최고 유사도", nullable = true, example = "85.7") BigDecimal bestSimilarity,
    @Schema(description = "시도 횟수", nullable = true, example = "15") Integer attemptCount,
    @Schema(description = "클리어 여부", nullable = true, example = "false") Boolean cleared) {

  public static YesterdayMeResponse from(
      LocalDate date, DailySentence sentence, GameSession session, String bestGuessText) {
    return new YesterdayMeResponse(
        date,
        true,
        session.getFinalRank(),
        sentence.getTotalPlayers(),
        bestGuessText,
        session.getBestSimilarity(),
        session.getAttemptCount(),
        session.isCleared());
  }

  public static YesterdayMeResponse notParticipated(LocalDate date) {
    return new YesterdayMeResponse(date, false, null, null, null, null, null, null);
  }
}
