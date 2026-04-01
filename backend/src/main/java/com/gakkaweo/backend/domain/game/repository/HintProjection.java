package com.gakkaweo.backend.domain.game.repository;

import java.math.BigDecimal;

public interface HintProjection {

  String getGuessText();

  BigDecimal getSimilarity();
}
