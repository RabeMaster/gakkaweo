package com.gakkaweo.backend.admin.dto;

import java.time.LocalDate;
import java.util.List;

public record TrendDataResponse(List<DailyTrend> trends) {

  public record DailyTrend(
      LocalDate date, long participants, long clears, double clearRate, long newMembers) {}
}
