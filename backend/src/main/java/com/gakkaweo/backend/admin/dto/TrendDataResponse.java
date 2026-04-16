package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "추이 데이터")
public record TrendDataResponse(@Schema(description = "일별 추이 목록") List<DailyTrend> trends) {

  @Schema(description = "일별 추이")
  public record DailyTrend(
      @Schema(description = "날짜", example = "2026-04-17") LocalDate date,
      @Schema(description = "참여자 수", example = "42") long participants,
      @Schema(description = "클리어 수", example = "30") long clears,
      @Schema(description = "클리어율", example = "71.4") double clearRate,
      @Schema(description = "신규 가입자 수", example = "5") long newMembers) {}
}
