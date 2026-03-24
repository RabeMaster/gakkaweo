package com.gakkaweo.backend.ranking.controller;

import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.ranking.sse.SseConnectionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class RankingController {

  private final RankingService rankingService;
  private final SseConnectionManager sseConnectionManager;

  @GetMapping("/today")
  public ResponseEntity<RankingResponse> getTodayRanking(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    if (userDetails == null) {
      return ResponseEntity.ok(rankingService.getRankings());
    }
    return ResponseEntity.ok(rankingService.getRankingsForUser(userDetails.publicId()));
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamRanking() {
    return sseConnectionManager.register();
  }
}
