package com.gakkaweo.backend.ranking.service;

import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_DETAIL_PREFIX;
import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_KEY_PREFIX;
import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_MEMBER_PREFIX;

import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.dto.RankingResponse.RankingEntry;
import com.gakkaweo.backend.ranking.dto.RankingSnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RankingService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int TOP_RANKING_SIZE = 10;
  private static final Duration EXPIRE_TTL = Duration.ofHours(1);

  private final StringRedisTemplate redisTemplate;

  public Integer updateRanking(GameSession session, Member member) {
    try {
      LocalDate today = LocalDate.now(KST);
      String rankingKey = buildRankingKey(today);
      String memberKey = RANKING_MEMBER_PREFIX + member.getPublicId();
      String detailKey = buildDetailKey(today, member.getPublicId());

      long elapsedSeconds = calculateElapsedSeconds();
      double score =
          encodeScore(session.getBestSimilarity(), session.getAttemptCount(), elapsedSeconds);

      redisTemplate.opsForZSet().add(rankingKey, memberKey, score);

      Map<String, String> detail =
          Map.of(
              "publicId", member.getPublicId().toString(),
              "nickname", member.getNickname(),
              "similarity", session.getBestSimilarity().toPlainString(),
              "attemptCount", String.valueOf(session.getAttemptCount()),
              "elapsedSeconds", String.valueOf(elapsedSeconds));
      redisTemplate.opsForHash().putAll(detailKey, detail);

      Long rank = redisTemplate.opsForZSet().reverseRank(rankingKey, memberKey);
      if (rank != null) {
        return rank.intValue() + 1;
      }
      return null;
    } catch (DataAccessException e) {
      log.warn("랭킹 갱신 실패: memberId={}", member.getPublicId(), e);
      return null;
    }
  }

  public RankingResponse getRankings() {
    try {
      LocalDate today = LocalDate.now(KST);
      String rankingKey = buildRankingKey(today);

      Set<String> topMembers =
          redisTemplate.opsForZSet().reverseRange(rankingKey, 0, TOP_RANKING_SIZE - 1);

      Long totalPlayers = redisTemplate.opsForZSet().zCard(rankingKey);
      if (totalPlayers == null) {
        totalPlayers = 0L;
      }

      if (topMembers == null || topMembers.isEmpty()) {
        return new RankingResponse(List.of(), totalPlayers);
      }

      List<RankingEntry> entries = new ArrayList<>();
      long rank = 1;
      for (String memberKey : topMembers) {
        String publicIdStr = memberKey.substring(RANKING_MEMBER_PREFIX.length());
        String detailKey = buildDetailKey(today, UUID.fromString(publicIdStr));

        Map<Object, Object> detail = redisTemplate.opsForHash().entries(detailKey);
        if (detail.isEmpty()) {
          continue;
        }

        entries.add(
            new RankingEntry(
                rank,
                UUID.fromString((String) detail.get("publicId")),
                (String) detail.get("nickname"),
                new BigDecimal((String) detail.get("similarity")),
                Integer.parseInt((String) detail.get("attemptCount"))));
        rank++;
      }

      return new RankingResponse(entries, totalPlayers);
    } catch (Exception e) {
      log.warn("랭킹 목록 조회 실패: {}", e.getMessage(), e);
      return new RankingResponse(List.of(), 0);
    }
  }

  public RankingSnapshot getAllRankingsForDate(LocalDate date) {
    try {
      String rankingKey = buildRankingKey(date);
      Long totalPlayers = redisTemplate.opsForZSet().zCard(rankingKey);
      if (totalPlayers == null || totalPlayers == 0) {
        return new RankingSnapshot(List.of(), 0);
      }

      Set<String> allMembers = redisTemplate.opsForZSet().reverseRange(rankingKey, 0, -1);
      if (allMembers == null || allMembers.isEmpty()) {
        return new RankingSnapshot(List.of(), 0);
      }

      List<RankingSnapshot.MemberRank> memberRanks = new ArrayList<>();
      int rank = 1;
      for (String memberKey : allMembers) {
        UUID publicId = UUID.fromString(memberKey.substring(RANKING_MEMBER_PREFIX.length()));
        memberRanks.add(new RankingSnapshot.MemberRank(publicId, rank++));
      }

      return new RankingSnapshot(memberRanks, totalPlayers.intValue());
    } catch (Exception e) {
      log.warn("랭킹 스냅샷 조회 실패: date={}", date, e);
      return new RankingSnapshot(List.of(), 0);
    }
  }

  public void expirePreviousDayRankingKeys(LocalDate date) {
    String rankingKey = buildRankingKey(date);

    Set<String> members = redisTemplate.opsForZSet().range(rankingKey, 0, -1);
    int detailCount = 0;
    if (members != null) {
      for (String memberKey : members) {
        String publicIdStr = memberKey.substring(RANKING_MEMBER_PREFIX.length());
        String detailKey = buildDetailKey(date, UUID.fromString(publicIdStr));
        redisTemplate.expire(detailKey, EXPIRE_TTL);
        detailCount++;
      }
    }

    redisTemplate.expire(rankingKey, EXPIRE_TTL);

    log.info("전날 랭킹 키 TTL 설정: date={}, detailKeys={}", date, detailCount);
  }

  private double encodeScore(BigDecimal similarity, int attemptCount, long elapsedSeconds) {
    long similarityComponent = similarity.multiply(BigDecimal.TEN).longValue() * 1_000_000_000L;
    long attemptComponent = (long) attemptCount * 100_000L;
    return similarityComponent - attemptComponent - elapsedSeconds;
  }

  private long calculateElapsedSeconds() {
    ZonedDateTime now = ZonedDateTime.now(KST);
    ZonedDateTime startOfDay = now.toLocalDate().atStartOfDay(KST);
    return Duration.between(startOfDay, now).getSeconds();
  }

  private String buildRankingKey(LocalDate date) {
    return RANKING_KEY_PREFIX + date;
  }

  private String buildDetailKey(LocalDate date, UUID memberPublicId) {
    return RANKING_DETAIL_PREFIX + date + ":" + RANKING_MEMBER_PREFIX + memberPublicId;
  }
}
