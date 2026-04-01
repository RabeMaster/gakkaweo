package com.gakkaweo.backend.game.dto;

import java.math.BigDecimal;
import java.util.List;

public record HintResponse(List<HintEntry> hints) {

  public record HintEntry(String guessText, BigDecimal similarity) {}
}
