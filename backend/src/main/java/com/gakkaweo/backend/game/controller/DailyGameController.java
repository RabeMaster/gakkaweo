package com.gakkaweo.backend.game.controller;

import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.game.dto.GameStatusResponse;
import com.gakkaweo.backend.game.dto.GuessHistoryResponse;
import com.gakkaweo.backend.game.dto.GuessRequest;
import com.gakkaweo.backend.game.dto.GuessResponse;
import com.gakkaweo.backend.game.dto.HintResponse;
import com.gakkaweo.backend.game.dto.TodayResponse;
import com.gakkaweo.backend.game.service.DailyGameService;
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
public class DailyGameController {

  private final DailyGameService dailyGameService;

  @GetMapping("/today")
  public ResponseEntity<TodayResponse> getToday() {
    return ResponseEntity.ok(dailyGameService.getToday());
  }

  @PostMapping("/guess")
  public ResponseEntity<GuessResponse> guess(
      @Valid @RequestBody GuessRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    if (userDetails == null) {
      return ResponseEntity.ok(dailyGameService.guessAnonymous(request));
    }
    return ResponseEntity.ok(dailyGameService.guessAuthenticated(request, userDetails.publicId()));
  }

  @GetMapping("/history")
  public ResponseEntity<GuessHistoryResponse> getHistory(
      @RequestParam UUID sentenceId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(dailyGameService.getHistory(sentenceId, userDetails.publicId()));
  }

  @GetMapping("/hints")
  public ResponseEntity<HintResponse> getHints(
      @RequestParam UUID sentenceId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(dailyGameService.getHints(sentenceId, userDetails.publicId()));
  }

  @GetMapping("/status")
  public ResponseEntity<GameStatusResponse> getStatus(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(dailyGameService.getStatus(userDetails.publicId()));
  }
}
