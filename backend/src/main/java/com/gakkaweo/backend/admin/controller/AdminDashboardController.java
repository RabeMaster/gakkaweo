package com.gakkaweo.backend.admin.controller;

import com.gakkaweo.backend.admin.dto.DateStatsResponse;
import com.gakkaweo.backend.admin.dto.FullRankingResponse;
import com.gakkaweo.backend.admin.dto.GuessLogResponse;
import com.gakkaweo.backend.admin.dto.TodayWidgetResponse;
import com.gakkaweo.backend.admin.dto.TrendDataResponse;
import com.gakkaweo.backend.admin.service.AdminDashboardService;
import com.gakkaweo.backend.config.openapi.AdminErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin: Dashboard", description = "어드민 대시보드")
@SecurityRequirement(name = "cookieAuth")
public class AdminDashboardController {

  private final AdminDashboardService adminDashboardService;

  @Operation(summary = "오늘 현황 조회")
  @AdminErrorResponses
  @GetMapping("/today")
  public ResponseEntity<TodayWidgetResponse> getTodayWidget() {
    return ResponseEntity.ok(adminDashboardService.getTodayWidget());
  }

  @Operation(summary = "전체 랭킹 조회")
  @AdminErrorResponses
  @GetMapping("/ranking")
  public ResponseEntity<FullRankingResponse> getFullRanking(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return ResponseEntity.ok(adminDashboardService.getFullRanking(date));
  }

  @Operation(summary = "날짜별 통계 조회")
  @AdminErrorResponses
  @GetMapping("/stats/{date}")
  public ResponseEntity<DateStatsResponse> getDateStats(
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return ResponseEntity.ok(adminDashboardService.getDateStats(date));
  }

  @Operation(summary = "추이 데이터 조회")
  @AdminErrorResponses
  @GetMapping("/trends")
  public ResponseEntity<TrendDataResponse> getTrends(@RequestParam(defaultValue = "30") int days) {
    return ResponseEntity.ok(adminDashboardService.getTrends(days));
  }

  @Operation(summary = "추측 로그 조회")
  @AdminErrorResponses
  @GetMapping("/guess-log")
  public ResponseEntity<GuessLogResponse> getGuessLog(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false) UUID memberPublicId) {
    return ResponseEntity.ok(adminDashboardService.getGuessLog(date, memberPublicId));
  }
}
