package com.gakkaweo.backend.ranking.controller;

import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.ranking.sse.SseConnectionManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ranking")
public class RankingController {

  private final RankingService rankingService;
  private final SseConnectionManager sseConnectionManager;

  public RankingController(
      RankingService rankingService, SseConnectionManager sseConnectionManager) {
    this.rankingService = rankingService;
    this.sseConnectionManager = sseConnectionManager;
  }

  @GetMapping("/today")
  public ResponseEntity<RankingResponse> getTodayRanking() {
    return ResponseEntity.ok(rankingService.getRankings());
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamRanking() {
    return sseConnectionManager.register();
  }
}
