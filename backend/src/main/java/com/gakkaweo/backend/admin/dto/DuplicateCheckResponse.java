package com.gakkaweo.backend.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record DuplicateCheckResponse(boolean hasDuplicate, List<SimilarEntry> similarEntries) {

  public record SimilarEntry(String sentence, BigDecimal similarity) {}
}
