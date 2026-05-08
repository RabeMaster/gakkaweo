package com.gakkaweo.backend.ranking.service;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.common.redis.RedisKeyConstants;
import com.gakkaweo.backend.domain.game.GameConstants;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.dto.RankingResponse.MyRank;
import com.gakkaweo.backend.ranking.dto.RankingResponse.RankingEntry;
import com.gakkaweo.backend.ranking.dto.RankingSnapshot;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RankingService {

  private static final int TOP_RANKING_SIZE = 10;
  private static final Duration EXPIRE_TTL = Duration.ofHours(1);
  private static final Duration LIVE_TTL = Duration.ofHours(30);

  private final StringRedisTemplate redisTemplate;
  private final DailySentenceRepository dailySentenceRepository;
  private final GameSessionRepository gameSessionRepository;
  private final MemberRepository memberRepository;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  private Counter rankingUpdateCounter;

  @PostConstruct
  void initCounters() {
    rankingUpdateCounter =
        Counter.builder("ranking.update")
            .description("Ranking update count")
            .register(meterRegistry);
  }

  public Integer updateRanking(GameSession session, Member member) {
    try {
      LocalDate today = LocalDate.now(clock);
      String rankingKey = RedisKeyConstants.rankingKey(today);
      String memberKey = RedisKeyConstants.memberKey(member.getPublicId());
      String detailKey = RedisKeyConstants.rankingDetailKey(today, member.getPublicId());

      ZonedDateTime startOfDay = today.atStartOfDay(KST);
      long elapsedSeconds = Duration.between(startOfDay, ZonedDateTime.now(clock)).getSeconds();
      Long clearedAtSeconds = calculateClearedAtSeconds(session, startOfDay);
      double score =
          encodeScore(
              session.getBestSimilarity(),
              session.getAttemptCount(),
              elapsedSeconds,
              clearedAtSeconds);

      redisTemplate.opsForZSet().add(rankingKey, memberKey, score);

      Map<String, String> detail =
          Map.of(
              "publicId", member.getPublicId().toString(),
              "nickname", member.getNickname(),
              "profileUrl", Objects.toString(member.getProfileUrl(), ""),
              "similarity", session.getBestSimilarity().toPlainString(),
              "attemptCount", String.valueOf(session.getAttemptCount()),
              "elapsedSeconds", String.valueOf(elapsedSeconds));
      redisTemplate.opsForHash().putAll(detailKey, detail);

      redisTemplate.expire(rankingKey, LIVE_TTL);
      redisTemplate.expire(detailKey, LIVE_TTL);

      rankingUpdateCounter.increment();

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
      LocalDate today = LocalDate.now(clock);
      return getRankingsForDate(today);
    } catch (Exception e) {
      log.warn("랭킹 목록 조회 실패: {}", e.getMessage(), e);
      return new RankingResponse(List.of(), 0);
    }
  }

  @Transactional(readOnly = true)
  public RankingResponse getRankingsForUser(UUID memberPublicId) {
    try {
      LocalDate today = LocalDate.now(clock);
      RankingResponse base = getRankingsForDate(today);

      MyRank myRank = lookupMyRank(today, memberPublicId);

      LocalDate yesterday = today.minusDays(1);
      Integer yesterdayRank = null;
      Integer yesterdayTotalPlayers = null;

      Optional<DailySentence> yesterdaySentence = dailySentenceRepository.findByUsedAt(yesterday);
      if (yesterdaySentence.isPresent()) {
        DailySentence ys = yesterdaySentence.get();
        yesterdayTotalPlayers = ys.getTotalPlayers();

        Optional<Member> member = memberRepository.findByPublicId(memberPublicId);
        if (member.isPresent()) {
          yesterdayRank =
              gameSessionRepository
                  .findByMemberAndSentence(member.get(), ys)
                  .map(GameSession::getFinalRank)
                  .orElse(null);
        }
      }

      return new RankingResponse(
          base.rankings(), base.totalPlayers(), myRank, yesterdayRank, yesterdayTotalPlayers);
    } catch (Exception e) {
      log.warn("사용자 랭킹 조회 실패: memberPublicId={}", memberPublicId, e);
      return new RankingResponse(List.of(), 0);
    }
  }

  public RankingResponse getFullRankingsForDate(LocalDate date) {
    try {
      String rankingKey = RedisKeyConstants.rankingKey(date);

      Set<String> allMembers = redisTemplate.opsForZSet().reverseRange(rankingKey, 0, -1);

      Long totalPlayers = redisTemplate.opsForZSet().zCard(rankingKey);
      if (totalPlayers == null) {
        totalPlayers = 0L;
      }

      if (allMembers == null || allMembers.isEmpty()) {
        return new RankingResponse(List.of(), totalPlayers);
      }

      return new RankingResponse(buildRankingEntries(allMembers, date), totalPlayers);
    } catch (Exception e) {
      log.warn("전체 랭킹 조회 실패: date={}", date, e);
      return new RankingResponse(List.of(), 0);
    }
  }

  private RankingResponse getRankingsForDate(LocalDate date) {
    String rankingKey = RedisKeyConstants.rankingKey(date);

    Set<String> topMembers =
        redisTemplate.opsForZSet().reverseRange(rankingKey, 0, TOP_RANKING_SIZE - 1);

    Long totalPlayers = redisTemplate.opsForZSet().zCard(rankingKey);
    if (totalPlayers == null) {
      totalPlayers = 0L;
    }

    if (topMembers == null || topMembers.isEmpty()) {
      return new RankingResponse(List.of(), totalPlayers);
    }

    return new RankingResponse(buildRankingEntries(topMembers, date), totalPlayers);
  }

  private List<RankingEntry> buildRankingEntries(Set<String> memberKeys, LocalDate date) {
    List<RankingEntry> entries = new ArrayList<>();
    long rank = 1;
    for (String memberKey : memberKeys) {
      UUID publicId = RedisKeyConstants.extractMemberPublicId(memberKey);
      String detailKey = RedisKeyConstants.rankingDetailKey(date, publicId);

      Map<Object, Object> detail = redisTemplate.opsForHash().entries(detailKey);
      if (detail.isEmpty()) {
        continue;
      }

      String profileUrl = (String) detail.get("profileUrl");

      entries.add(
          new RankingEntry(
              rank,
              UUID.fromString((String) detail.get("publicId")),
              (String) detail.get("nickname"),
              profileUrl == null || profileUrl.isEmpty() ? null : profileUrl,
              new BigDecimal((String) detail.get("similarity")),
              Integer.parseInt((String) detail.get("attemptCount"))));
      rank++;
    }
    return entries;
  }

  private MyRank lookupMyRank(LocalDate date, UUID memberPublicId) {
    String rankingKey = RedisKeyConstants.rankingKey(date);
    String memberKey = RedisKeyConstants.memberKey(memberPublicId);

    Long rank = redisTemplate.opsForZSet().reverseRank(rankingKey, memberKey);
    if (rank == null) {
      return null;
    }

    String detailKey = RedisKeyConstants.rankingDetailKey(date, memberPublicId);
    Map<Object, Object> detail = redisTemplate.opsForHash().entries(detailKey);
    if (detail.isEmpty()) {
      return null;
    }

    return new MyRank(
        rank + 1,
        new BigDecimal((String) detail.get("similarity")),
        Integer.parseInt((String) detail.get("attemptCount")));
  }

  public RankingSnapshot getAllRankingsForDate(LocalDate date) {
    try {
      String rankingKey = RedisKeyConstants.rankingKey(date);
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
        UUID publicId = RedisKeyConstants.extractMemberPublicId(memberKey);
        memberRanks.add(new RankingSnapshot.MemberRank(publicId, rank++));
      }

      return new RankingSnapshot(memberRanks, totalPlayers.intValue());
    } catch (Exception e) {
      log.warn("랭킹 스냅샷 조회 실패: date={}", date, e);
      return new RankingSnapshot(List.of(), 0);
    }
  }

  @Timed(value = "ranking.cache.rebuild.duration", description = "Ranking cache rebuild time")
  public int rebuildRankingCache(LocalDate date) {
    DailySentence sentence =
        dailySentenceRepository
            .findByUsedAt(date)
            .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));

    List<GameSession> sessions = gameSessionRepository.findAllBySentenceWithMember(sentence);

    String rankingKey = RedisKeyConstants.rankingKey(date);
    ZonedDateTime startOfDay = date.atStartOfDay(KST);
    int count = 0;

    for (GameSession session : sessions) {
      Member member = session.getMember();
      if (member == null) {
        continue;
      }

      long elapsedSeconds =
          Duration.between(startOfDay, session.getUpdatedAt().atZone(KST)).getSeconds();
      if (elapsedSeconds < 0) {
        elapsedSeconds = 0;
      }

      Long clearedAtSeconds = calculateClearedAtSeconds(session, startOfDay);
      double score =
          encodeScore(
              session.getBestSimilarity(),
              session.getAttemptCount(),
              elapsedSeconds,
              clearedAtSeconds);

      String memberKey = RedisKeyConstants.memberKey(member.getPublicId());
      redisTemplate.opsForZSet().add(rankingKey, memberKey, score);

      String detailKey = RedisKeyConstants.rankingDetailKey(date, member.getPublicId());
      Map<String, String> detail =
          Map.of(
              "publicId", member.getPublicId().toString(),
              "nickname", member.getNickname(),
              "profileUrl", Objects.toString(member.getProfileUrl(), ""),
              "similarity", session.getBestSimilarity().toPlainString(),
              "attemptCount", String.valueOf(session.getAttemptCount()),
              "elapsedSeconds", String.valueOf(elapsedSeconds));
      redisTemplate.opsForHash().putAll(detailKey, detail);
      redisTemplate.expire(detailKey, LIVE_TTL);
      count++;
    }

    if (count > 0) {
      redisTemplate.expire(rankingKey, LIVE_TTL);
    }

    log.info("랭킹 캐시 재구축: date={}, sessions={}", date, count);
    return count;
  }

  public boolean cleanupMemberRanking(UUID publicId) {
    try {
      LocalDate today = LocalDate.now(clock);
      String rankingKey = RedisKeyConstants.rankingKey(today);
      String memberKey = RedisKeyConstants.memberKey(publicId);
      String detailKey = RedisKeyConstants.rankingDetailKey(today, publicId);

      Long removed = redisTemplate.opsForZSet().remove(rankingKey, memberKey);
      if (removed != null && removed > 0) {
        redisTemplate.delete(detailKey);
        return true;
      }
      return false;
    } catch (Exception e) {
      log.warn("회원 랭킹 정리 실패: publicId={}", publicId, e);
      return false;
    }
  }

  public void expirePreviousDayRankingKeys(LocalDate date) {
    String rankingKey = RedisKeyConstants.rankingKey(date);

    Set<String> members = redisTemplate.opsForZSet().range(rankingKey, 0, -1);
    int detailCount = 0;
    if (members != null) {
      for (String memberKey : members) {
        UUID publicId = RedisKeyConstants.extractMemberPublicId(memberKey);
        String detailKey = RedisKeyConstants.rankingDetailKey(date, publicId);
        redisTemplate.expire(detailKey, EXPIRE_TTL);
        detailCount++;
      }
    }

    redisTemplate.expire(rankingKey, EXPIRE_TTL);

    log.info("전날 랭킹 키 TTL 설정: date={}, detailKeys={}", date, detailCount);
  }

  private double encodeScore(
      BigDecimal similarity, int attemptCount, long elapsedSeconds, Long clearedAtSeconds) {
    long similarityComponent = similarity.multiply(BigDecimal.TEN).longValue() * 1_000_000_000L;

    if (clearedAtSeconds != null && similarity.compareTo(GameConstants.PERFECT_SIMILARITY) >= 0) {
      return similarityComponent - clearedAtSeconds;
    }

    long attemptComponent = (long) attemptCount * 100_000L;
    return similarityComponent - attemptComponent - elapsedSeconds;
  }

  private Long calculateClearedAtSeconds(GameSession session, ZonedDateTime startOfDay) {
    if (session.getClearedAt() == null) {
      return null;
    }
    long seconds = Duration.between(startOfDay, session.getClearedAt().atZone(KST)).getSeconds();
    return Math.max(seconds, 0);
  }
}
