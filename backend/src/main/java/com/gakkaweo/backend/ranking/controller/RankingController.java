package com.gakkaweo.backend.ranking.controller;

import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.config.openapi.StandardErrorResponses;
import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.ranking.sse.SseConnectionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Ranking", description = "랭킹 조회 및 실시간 스트림")
public class RankingController {

  private final RankingService rankingService;
  private final SseConnectionManager sseConnectionManager;

  @Operation(summary = "오늘 랭킹 조회", description = "인증 시 myRank, yesterdayRank 추가 반환")
  @StandardErrorResponses
  @GetMapping("/today")
  public ResponseEntity<RankingResponse> getTodayRanking(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    if (userDetails == null) {
      return ResponseEntity.ok(rankingService.getRankings());
    }
    return ResponseEntity.ok(rankingService.getRankingsForUser(userDetails.publicId()));
  }

  @Operation(
      summary = "랭킹 SSE 스트림",
      description =
          "이벤트: RANKING_UPDATE (랭킹 변경), DAY_CHANGE (자정 전환), "
              + "ANNOUNCEMENT (공지), HEARTBEAT (10초 간격 연결 유지). 최대 500 동시 연결\n\n"
              + "에러 코드:\n- `SSE_MAX_CONNECTIONS` (503): 최대 연결 수 초과")
  @StandardErrorResponses
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamRanking() {
    return sseConnectionManager.register();
  }
}
