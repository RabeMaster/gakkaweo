package com.gakkaweo.backend.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "힌트 응답")
public record HintResponse(@Schema(description = "힌트 목록") List<HintEntry> hints) {

  @Schema(description = "힌트 항목")
  public record HintEntry(
      @Schema(description = "다른 플레이어의 추측 텍스트", example = "오늘 날씨가 좋다") String guessText,
      @Schema(description = "유사도", example = "45.2") BigDecimal similarity) {}
}
