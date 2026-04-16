package com.gakkaweo.backend.game.controller;

import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.config.openapi.StandardErrorResponses;
import com.gakkaweo.backend.game.dto.GameStatusResponse;
import com.gakkaweo.backend.game.dto.GuessHistoryResponse;
import com.gakkaweo.backend.game.dto.GuessRequest;
import com.gakkaweo.backend.game.dto.GuessResponse;
import com.gakkaweo.backend.game.dto.HintResponse;
import com.gakkaweo.backend.game.dto.TodayResponse;
import com.gakkaweo.backend.game.service.DailyGameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/daily")
@RequiredArgsConstructor
@Tag(name = "Daily Game", description = "데일리 게임 (추측, 히스토리, 힌트)")
public class DailyGameController {

  private final DailyGameService dailyGameService;

  @Operation(summary = "오늘 문제 조회")
  @StandardErrorResponses
  @GetMapping("/today")
  public ResponseEntity<TodayResponse> getToday() {
    return ResponseEntity.ok(dailyGameService.getToday());
  }

  @Operation(
      summary = "추측 제출",
      description = "익명: 유사도만 반환 (attemptNumber/gameStatus null). 로그인: 세션 저장 + 전체 응답")
  @StandardErrorResponses
  @PostMapping("/guess")
  public ResponseEntity<GuessResponse> guess(
      @Valid @RequestBody GuessRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    if (userDetails == null) {
      return ResponseEntity.ok(dailyGameService.guessAnonymous(request));
    }
    return ResponseEntity.ok(dailyGameService.guessAuthenticated(request, userDetails.publicId()));
  }

  @Operation(summary = "추측 히스토리 조회")
  @SecurityRequirement(name = "cookieAuth")
  @StandardErrorResponses
  @GetMapping("/history")
  public ResponseEntity<GuessHistoryResponse> getHistory(
      @RequestParam UUID sentenceId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(dailyGameService.getHistory(sentenceId, userDetails.publicId()));
  }

  @Operation(summary = "힌트 조회", description = "bestSimilarity 60% 이상 필요. 다른 유저의 추측 최대 5개")
  @SecurityRequirement(name = "cookieAuth")
  @StandardErrorResponses
  @GetMapping("/hints")
  public ResponseEntity<HintResponse> getHints(
      @RequestParam UUID sentenceId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(dailyGameService.getHints(sentenceId, userDetails.publicId()));
  }

  @Operation(summary = "게임 상태 조회")
  @SecurityRequirement(name = "cookieAuth")
  @StandardErrorResponses
  @GetMapping("/status")
  public ResponseEntity<GameStatusResponse> getStatus(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(dailyGameService.getStatus(userDetails.publicId()));
  }
}
