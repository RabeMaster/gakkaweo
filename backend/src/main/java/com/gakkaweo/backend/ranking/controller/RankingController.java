package com.gakkaweo.backend.ranking.controller;

import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.service.RankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ranking")
public class RankingController {

  private final RankingService rankingService;

  public RankingController(RankingService rankingService) {
    this.rankingService = rankingService;
  }

  @GetMapping("/today")
  public ResponseEntity<RankingResponse> getTodayRanking() {
    return ResponseEntity.ok(rankingService.getRankings());
  }
}
