package com.gakkaweo.backend.admin.controller;

import com.gakkaweo.backend.admin.dto.DateStatsResponse;
import com.gakkaweo.backend.admin.dto.FullRankingResponse;
import com.gakkaweo.backend.admin.dto.GuessLogResponse;
import com.gakkaweo.backend.admin.dto.TodayWidgetResponse;
import com.gakkaweo.backend.admin.dto.TrendDataResponse;
import com.gakkaweo.backend.admin.service.AdminDashboardService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

  private final AdminDashboardService adminDashboardService;

  @GetMapping("/today")
  public ResponseEntity<TodayWidgetResponse> getTodayWidget() {
    return ResponseEntity.ok(adminDashboardService.getTodayWidget());
  }

  @GetMapping("/ranking")
  public ResponseEntity<FullRankingResponse> getFullRanking(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return ResponseEntity.ok(adminDashboardService.getFullRanking(date));
  }

  @GetMapping("/stats/{date}")
  public ResponseEntity<DateStatsResponse> getDateStats(
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return ResponseEntity.ok(adminDashboardService.getDateStats(date));
  }

  @GetMapping("/trends")
  public ResponseEntity<TrendDataResponse> getTrends(@RequestParam(defaultValue = "30") int days) {
    return ResponseEntity.ok(adminDashboardService.getTrends(days));
  }

  @GetMapping("/guess-log")
  public ResponseEntity<GuessLogResponse> getGuessLog(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false) UUID memberPublicId) {
    return ResponseEntity.ok(adminDashboardService.getGuessLog(date, memberPublicId));
  }
}
