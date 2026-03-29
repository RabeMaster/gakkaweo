package com.gakkaweo.backend.admin.dto;

import java.math.BigDecimal;

public record SimilarityTestResponse(String sentence, String guessText, BigDecimal similarity) {}
