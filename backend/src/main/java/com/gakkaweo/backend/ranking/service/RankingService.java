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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RankingService {

  private static final int TOP_RANKING_SIZE = 10;
  private static final Duration EXPIRE_TTL = Duration.ofHours(1);
  private static final Duration LIVE_TTL = Duration.ofHours(30);
  private static final long MAX_RANKING_ELAPSED_MILLIS = Duration.ofDays(1).toMillis() - 1;

  // 999회와 하루 경과 밀리초를 합해도 0.1점의 점수 간격을 침범하지 않습니다.
  private static final int ATTEMPT_COUNT_SCORE_CAP = 999;

  private final StringRedisTemplate redisTemplate;
  private final DailySentenceRepository dailySentenceRepository;
  private final GameSessionRepository gameSessionRepository;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final RankingWriteCoordinator writeCoordinator;
  private final ReentrantLock recoveryLock = new ReentrantLock();
  private final ConcurrentHashMap<LocalDate, Long> dirtyDates = new ConcurrentHashMap<>();
  private Counter rankingUpdateCounter;
  // Redis 최초 실패 후에는 주기적 복구 작업만 접속을 재시도합니다.
  // 최고 기록 갱신마다 커밋 잠금을 잡고 Redis 타임아웃을 기다리는 일을 방지합니다.
  private volatile boolean cacheCircuitOpen;

  private static boolean isDetailIncomplete(Map<?, ?> detail) {
    return detail.get("publicId") == null
        || detail.get("nickname") == null
        || detail.get("similarity") == null
        || detail.get("attemptCount") == null;
  }

  @PostConstruct
  void initCounters() {
    markRecoveryNeeded(LocalDate.now(clock));
    rankingUpdateCounter =
        Counter.builder("ranking.update")
            .description("Ranking update count")
            .register(meterRegistry);
  }

  public Integer updateRanking(GameSession submittedSession, Member submittedMember) {
    GameSession session = submittedSession;
    Member member = submittedMember;
    writeCoordinator.lock();
    try {
      LocalDate today = session.getSentence().getUsedAt();
      if (today == null) {
        return null;
      }
      if (cacheCircuitOpen) {
        markRecoveryNeeded(today);
        return null;
      }
      // 늦게 실행된 콜백이 탈퇴한 회원이나 교체된 문제의 세션을 복구하지 않도록 합니다.
      if (!gameSessionRepository.existsByIdAndMemberPublicIdAndSentencePublicIdAndSentenceUsedAt(
          session.getId(), member.getPublicId(), session.getSentence().getPublicId(), today)) {
        markRecoveryNeeded(today);
        return null;
      }
      // 커밋 이후 콜백 순서가 바뀌어도 최신 DB 기록과 회원 정보를 사용합니다.
      session =
          gameSessionRepository.findAllBySentenceWithMember(session.getSentence()).stream()
              .filter(candidate -> candidate.getId().equals(submittedSession.getId()))
              .findFirst()
              .orElse(null);
      if (session == null || session.getMember() == null) {
        markRecoveryNeeded(today);
        return null;
      }
      member = session.getMember();
      String rankingKey = RedisKeyConstants.rankingKey(today);
      String memberKey = RedisKeyConstants.memberKey(member.getPublicId());
      String detailKey = RedisKeyConstants.rankingDetailKey(today, member.getPublicId());

      ZonedDateTime startOfDay = today.atStartOfDay(KST);
      long elapsedMillis = elapsedMillis(session, startOfDay);
      Long clearedAtMillis = calculateClearedAtMillis(session, startOfDay);
      double score =
          encodeScore(
              session.getBestSimilarity(),
              rankingAttempts(session),
              elapsedMillis,
              clearedAtMillis);

      Map<String, String> detail =
          Map.of(
              "publicId", member.getPublicId().toString(),
              "nickname", member.getNickname(),
              "profileUrl", Objects.toString(member.getProfileUrl(), ""),
              "similarity", session.getBestSimilarity().toPlainString(),
              "attemptCount", String.valueOf(rankingAttempts(session)),
              "elapsedMillis", String.valueOf(elapsedMillis));
      List<String> args =
          new ArrayList<>(
              List.of(memberKey, String.valueOf(score), String.valueOf(LIVE_TTL.toSeconds())));
      detail.forEach(
          (key, value) -> {
            args.add(key);
            args.add(value);
          });
      // ZSET·상세 정보·TTL을 함께 갱신하고 오래된 최고 기록이 최신 기록을 덮어쓰지 않도록 합니다.
      Long rank;
      try {
        rank =
            redisTemplate.execute(
                new DefaultRedisScript<>(
                    """
          local ztype = redis.call('TYPE', KEYS[1]).ok
          local htype = redis.call('TYPE', KEYS[2]).ok
          if (ztype ~= 'none' and ztype ~= 'zset') or (htype ~= 'none' and htype ~= 'hash') then
            return redis.error_reply('Invalid ranking key type')
          end
          if not tonumber(ARGV[2]) or not tonumber(ARGV[3]) or tonumber(ARGV[3]) <= 0 or #ARGV ~= 15 then
            return redis.error_reply('Invalid ranking values')
          end
          local values = {}
          for i = 4, #ARGV, 2 do values[ARGV[i]] = ARGV[i + 1] end
          local similarity = tonumber(values['similarity'])
          local attempts = tonumber(values['attemptCount'])
          local elapsed = tonumber(values['elapsedMillis'])
          if not values['publicId'] or ARGV[1] ~= 'member:' .. values['publicId']
              or not values['nickname'] or values['nickname'] == '' or not values['profileUrl']
              or not similarity or similarity < 0 or similarity > 100
              or not attempts or attempts < 0 or not elapsed or elapsed < 0 then
            return redis.error_reply('Invalid ranking detail values')
          end
          local old = redis.call('ZSCORE', KEYS[1], ARGV[1])
          if not old or tonumber(old) <= tonumber(ARGV[2]) then
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
            redis.call('HSET', KEYS[2], unpack(ARGV, 4))
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
          end
          return redis.call('ZREVRANK', KEYS[1], ARGV[1])
          """,
                    Long.class),
                List.of(rankingKey, detailKey),
                args.toArray());
      } catch (DataAccessException redisFailure) {
        recordCacheFailure(today);
        log.warn("랭킹 Redis 갱신 실패: memberId={}", member.getPublicId(), redisFailure);
        return null;
      }

      rankingUpdateCounter.increment();

      if (rank != null) {
        return rank.intValue() + 1;
      }
      return null;
    } catch (DataAccessException e) {
      markRecoveryNeeded(submittedSession.getSentence().getUsedAt());
      log.warn("랭킹 callback DB 확인 실패: memberId={}", submittedMember.getPublicId(), e);
      return null;
    } finally {
      writeCoordinator.unlock();
    }
  }

  public RankingResponse getRankings() {
    return getRankingsForUser(null);
  }

  public RankingResponse getRankingsForUser(UUID memberPublicId) {
    return getRankingsForUser(memberPublicId, null);
  }

  public RankingResponse getRankingsForUser(UUID memberPublicId, UUID sentencePublicId) {
    LocalDate today = LocalDate.now(clock);
    return readRankings(
        today,
        sentencePublicId,
        () -> {
          RankingResponse base = getRankingsForDate(today);
          MyRank myRank = memberPublicId == null ? null : lookupMyRank(today, memberPublicId);
          return new RankingResponse(base.rankings(), base.totalPlayers(), myRank);
        });
  }

  public RankingResponse getFullRankingsForDate(LocalDate date) {
    return readRankings(
        date,
        null,
        () -> {
          String key = RedisKeyConstants.rankingKey(date);
          Set<String> members = redisTemplate.opsForZSet().reverseRange(key, 0, -1);
          Long total = redisTemplate.opsForZSet().zCard(key);
          return new RankingResponse(
              buildRankingEntries(members == null ? Set.of() : members, date),
              total == null ? 0 : total);
        });
  }

  private RankingResponse readRankings(
      LocalDate date, UUID sentencePublicId, Supplier<RankingResponse> reader) {
    // 정상 조회는 한 번 대조하고 같은 잠금 안에서 응답을 만듭니다. 복구 후에는 다시 확인합니다.
    for (int attempt = 0; attempt < 2; attempt++) {
      writeCoordinator.lock();
      try {
        boolean sentenceMatches;
        try {
          sentenceMatches =
              sentencePublicId == null
                  || dailySentenceRepository
                      .findByUsedAt(date)
                      .map(sentence -> sentence.getPublicId().equals(sentencePublicId))
                      .orElse(false);
          if (sentenceMatches && !cacheCircuitOpen && !needsRecovery(date)) {
            return reader.get();
          }
        } catch (RuntimeException e) {
          unavailable(date, e);
          throw new BusinessException(ErrorCode.RANKING_UNAVAILABLE);
        }

        // 조회 거절 자체는 새로운 캐시 장애가 아니므로 복구 표식을 변경하지 않습니다.
        if (!sentenceMatches || cacheCircuitOpen || attempt == 1) {
          throw new BusinessException(ErrorCode.RANKING_UNAVAILABLE);
        }
      } finally {
        writeCoordinator.unlock();
      }

      // 재구축은 recoveryLock 다음에 조정 잠금을 잡으므로 조회 잠금을 먼저 해제합니다.
      try {
        rebuildRankingCache(date);
      } catch (RuntimeException e) {
        unavailable(date, e);
        throw new BusinessException(ErrorCode.RANKING_UNAVAILABLE);
      }
    }
    throw new BusinessException(ErrorCode.RANKING_UNAVAILABLE);
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
    if (memberKeys.isEmpty()) {
      return List.of();
    }
    List<UUID> publicIds =
        memberKeys.stream().map(RedisKeyConstants::extractMemberPublicId).toList();
    var serializer = redisTemplate.getStringSerializer();
    List<Object> details =
        redisTemplate.executePipelined(
            (RedisCallback<Object>)
                connection -> {
                  for (UUID publicId : publicIds) {
                    connection
                        .hashCommands()
                        .hGetAll(
                            serializer.serialize(
                                RedisKeyConstants.rankingDetailKey(date, publicId)));
                  }
                  return null;
                });

    List<RankingEntry> entries = new ArrayList<>();
    for (int i = 0; i < publicIds.size(); i++) {
      if (!(details.get(i) instanceof Map<?, ?> detail) || detail.isEmpty()) {
        throw new BusinessException(ErrorCode.RANKING_UNAVAILABLE);
      }
      if (isDetailIncomplete(detail)) {
        markRecoveryNeeded(date);
        throw new BusinessException(ErrorCode.RANKING_UNAVAILABLE);
      }

      String profileUrl = (String) detail.get("profileUrl");

      entries.add(
          new RankingEntry(
              i + 1L,
              UUID.fromString((String) detail.get("publicId")),
              (String) detail.get("nickname"),
              profileUrl == null || profileUrl.isEmpty() ? null : profileUrl,
              new BigDecimal((String) detail.get("similarity")),
              Integer.parseInt((String) detail.get("attemptCount"))));
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
      throw new BusinessException(ErrorCode.RANKING_UNAVAILABLE);
    }
    if (isDetailIncomplete(detail)) {
      throw new BusinessException(ErrorCode.RANKING_UNAVAILABLE);
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
      if (e instanceof DataAccessException) {
        recordCacheFailure(date);
      }
      // 데이터 없음과 Redis 실패를 구분해야 스케줄러가 재시도하고 보존할 수 있습니다.
      throw e;
    }
  }

  /** 최종 순위는 누락 가능성이 있는 Redis 캐시에 의존하지 않습니다. */
  public RankingSnapshot getCanonicalSnapshotForDate(LocalDate date) {
    var sentence = dailySentenceRepository.findByUsedAt(date);
    if (sentence.isEmpty()) {
      return new RankingSnapshot(List.of(), 0);
    }
    ZonedDateTime startOfDay = date.atStartOfDay(KST);
    var sessions =
        gameSessionRepository.findAllBySentenceWithMember(sentence.get()).stream()
            .filter(s -> s.getMember() != null && s.getBestSimilarity().signum() > 0)
            .sorted(
                Comparator.comparingDouble(
                        (GameSession s) ->
                            encodeScore(
                                s.getBestSimilarity(),
                                rankingAttempts(s),
                                elapsedMillis(s, startOfDay),
                                calculateClearedAtMillis(s, startOfDay)))
                    .reversed()
                    // Redis ZREVRANGE는 동점일 때 회원 키의 역순으로 정렬합니다.
                    .thenComparing(
                        s -> RedisKeyConstants.memberKey(s.getMember().getPublicId()),
                        Comparator.reverseOrder()))
            .toList();
    List<RankingSnapshot.MemberRank> ranks = new ArrayList<>();
    for (int i = 0; i < sessions.size(); i++) {
      ranks.add(new RankingSnapshot.MemberRank(sessions.get(i).getMember().getPublicId(), i + 1));
    }
    return new RankingSnapshot(ranks, ranks.size());
  }

  @Timed(value = "ranking.cache.rebuild.duration", description = "Ranking cache rebuild time")
  public int rebuildRankingCache(LocalDate date) {
    recoveryLock.lock();
    List<String> stagingKeys = new ArrayList<>();
    try {
      long expectedRevision = writeCoordinator.revision();
      Long dirtyBefore = dirtyDates.get(date);
      DailySentence sentence =
          dailySentenceRepository
              .findByUsedAt(date)
              .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));
      List<GameSession> sessions =
          gameSessionRepository.findAllBySentenceWithMember(sentence).stream()
              .filter(
                  session ->
                      session.getMember() != null && session.getBestSimilarity().signum() > 0)
              .toList();
      String key = RedisKeyConstants.rankingKey(date);
      String suffix = ":rebuild:" + UUID.randomUUID();
      String stagedRanking = key + suffix;
      stagingKeys.add(stagedRanking);
      List<String> swapKeys =
          new ArrayList<>(List.of(key, stagedRanking, RedisKeyConstants.rankingSyncKey(date)));
      ZonedDateTime start = date.atStartOfDay(KST);
      for (GameSession session : sessions) {
        Member member = session.getMember();
        String detailKey = RedisKeyConstants.rankingDetailKey(date, member.getPublicId());
        String stagedDetail = detailKey + suffix;
        stagingKeys.add(stagedDetail);
        redisTemplate
            .opsForZSet()
            .add(
                stagedRanking,
                RedisKeyConstants.memberKey(member.getPublicId()),
                encodeScore(
                    session.getBestSimilarity(),
                    rankingAttempts(session),
                    elapsedMillis(session, start),
                    calculateClearedAtMillis(session, start)));
        redisTemplate.expire(stagedRanking, Duration.ofMinutes(5));
        redisTemplate.opsForHash().putAll(stagedDetail, detail(session, start));
        redisTemplate.expire(stagedDetail, Duration.ofMinutes(5));
        swapKeys.add(detailKey);
        swapKeys.add(stagedDetail);
      }
      writeCoordinator.lock();
      try {
        if (expectedRevision != writeCoordinator.revision()
            || !dailySentenceRepository
                .findByUsedAt(date)
                .map(current -> current.getId().equals(sentence.getId()))
                .orElse(false)) {
          throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        redisTemplate.execute(
            new DefaultRedisScript<>(
                """
            local count = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            if not count or count < 0 or not ttl or ttl <= 0 or #KEYS ~= 3 + count * 2 then
              return redis.error_reply('Invalid staging arguments')
            end
            if count > 0 then
              if redis.call('TYPE', KEYS[2]).ok ~= 'zset' or redis.call('ZCARD', KEYS[2]) ~= count then
                return redis.error_reply('Invalid staged ranking')
              end
            end
            local seen = {}
            for i = 4, #KEYS, 2 do
              if redis.call('TYPE', KEYS[i + 1]).ok ~= 'hash' then
                return redis.error_reply('Invalid staged detail type')
              end
              local d = redis.call('HMGET', KEYS[i + 1], 'publicId', 'nickname', 'similarity', 'attemptCount', 'elapsedMillis', 'profileUrl')
              local similarity = tonumber(d[3])
              local attempts = tonumber(d[4])
              local elapsed = tonumber(d[5])
              if not d[1] or seen[d[1]] or not d[2] or d[2] == '' or not d[6]
                  or not similarity or similarity < 0 or similarity > 100
                  or not attempts or attempts < 0 or attempts ~= math.floor(attempts)
                  or not elapsed or elapsed < 0 or elapsed ~= math.floor(elapsed)
                  or string.sub(KEYS[i], -string.len(d[1])) ~= d[1]
                  or not redis.call('ZSCORE', KEYS[2], 'member:' .. d[1]) then
                return redis.error_reply('Invalid staged detail values')
              end
              seen[d[1]] = true
            end
            if count > 0 then
              redis.call('RENAME', KEYS[2], KEYS[1])
              redis.call('EXPIRE', KEYS[1], ttl)
            else
              redis.call('DEL', KEYS[1])
            end
            for i = 4, #KEYS, 2 do
              redis.call('RENAME', KEYS[i + 1], KEYS[i])
              redis.call('EXPIRE', KEYS[i], ttl)
            end
            redis.call('SET', KEYS[3], ARGV[3], 'EX', ttl)
            return count
            """,
                Long.class),
            swapKeys,
            String.valueOf(sessions.size()),
            String.valueOf(LIVE_TTL.toSeconds()),
            sentence.getPublicId().toString());
        if (dirtyBefore != null) {
          dirtyDates.remove(date, dirtyBefore);
        }
        cacheCircuitOpen = false;
      } finally {
        writeCoordinator.unlock();
      }
      return sessions.size();
    } catch (RuntimeException e) {
      unavailable(date, e);
      throw e;
    } finally {
      try {
        if (!stagingKeys.isEmpty()) {
          redisTemplate.delete(stagingKeys);
        }
      } catch (DataAccessException e) {
        log.debug("임시 랭킹 키는 TTL로 정리합니다: date={}", date);
      }
      recoveryLock.unlock();
    }
  }

  public void markRecoveryNeeded(LocalDate date) {
    if (date != null) {
      dirtyDates.merge(date, 1L, Long::sum);
    }
  }

  public void recordCacheFailure(LocalDate date) {
    markRecoveryNeeded(date);
    cacheCircuitOpen = true;
  }

  public boolean isCacheCircuitOpen() {
    return cacheCircuitOpen;
  }

  public boolean needsRecovery(LocalDate date) {
    if (dailySentenceRepository.findByUsedAt(date).isEmpty()) {
      return false;
    }
    if (cacheCircuitOpen || dirtyDates.containsKey(date)) {
      return true;
    }
    writeCoordinator.lock();
    try {
      var sentence = dailySentenceRepository.findByUsedAt(date);
      if (sentence.isEmpty()) {
        return false;
      }
      if (!sentence
          .get()
          .getPublicId()
          .toString()
          .equals(redisTemplate.opsForValue().get(RedisKeyConstants.rankingSyncKey(date)))) {
        return true;
      }
      List<GameSession> sessions =
          gameSessionRepository.findAllBySentenceWithMember(sentence.get()).stream()
              .filter(
                  session ->
                      session.getMember() != null && session.getBestSimilarity().signum() > 0)
              .toList();
      String key = RedisKeyConstants.rankingKey(date);
      if (redisTemplate.opsForZSet().zCard(key) != sessions.size()) {
        return true;
      }
      if (sessions.isEmpty()) {
        return false;
      }
      var serializer = redisTemplate.getStringSerializer();
      byte[] rankingKey = serializer.serialize(key);
      List<Object> cachedRecords =
          redisTemplate.executePipelined(
              (RedisCallback<Object>)
                  connection -> {
                    for (GameSession session : sessions) {
                      UUID id = session.getMember().getPublicId();
                      connection
                          .zSetCommands()
                          .zScore(
                              rankingKey, serializer.serialize(RedisKeyConstants.memberKey(id)));
                      connection
                          .hashCommands()
                          .hGetAll(
                              serializer.serialize(RedisKeyConstants.rankingDetailKey(date, id)));
                    }
                    return null;
                  });

      ZonedDateTime start = date.atStartOfDay(KST);
      for (int i = 0; i < sessions.size(); i++) {
        GameSession session = sessions.get(i);
        Double score = (Double) cachedRecords.get(i * 2);
        double expected =
            encodeScore(
                session.getBestSimilarity(),
                rankingAttempts(session),
                elapsedMillis(session, start),
                calculateClearedAtMillis(session, start));
        if (score == null
            || Double.compare(score, expected) != 0
            || !detail(session, start).equals(cachedRecords.get(i * 2 + 1))) {
          return true;
        }
      }
      return false;
    } catch (DataAccessException e) {
      // 잘못된 타입도 복구 대상이며 연결 실패 후에는 주기적 복구만 재접속합니다.
      recordCacheFailure(date);
      return true;
    } finally {
      writeCoordinator.unlock();
    }
  }

  private int rankingAttempts(GameSession session) {
    return session.getRankingAttemptCount() == null
        ? session.getAttemptCount()
        : session.getRankingAttemptCount();
  }

  private long elapsedMillis(GameSession session, ZonedDateTime startOfDay) {
    var achieved = session.getRankingAchievedAt();
    if (achieved == null) {
      achieved = session.getUpdatedAt() == null ? clock.instant() : session.getUpdatedAt();
    }
    return rankingElapsedMillis(Duration.between(startOfDay.toInstant(), achieved).toMillis());
  }

  public boolean cleanupMemberRanking(UUID publicId) {
    writeCoordinator.lock();
    try {
      LocalDate today = LocalDate.now(clock);
      if (cacheCircuitOpen) {
        markRecoveryNeeded(today);
        return false;
      }
      String rankingKey = RedisKeyConstants.rankingKey(today);
      String memberKey = RedisKeyConstants.memberKey(publicId);
      String detailKey = RedisKeyConstants.rankingDetailKey(today, publicId);

      Long removed = redisTemplate.opsForZSet().remove(rankingKey, memberKey);
      redisTemplate.delete(detailKey);
      if (removed != null && removed > 0) {
        return true;
      }
      return false;
    } catch (Exception e) {
      if (e instanceof DataAccessException) {
        recordCacheFailure(LocalDate.now(clock));
      } else {
        markRecoveryNeeded(LocalDate.now(clock));
      }
      log.warn("회원 랭킹 정리 실패: publicId={}", publicId, e);
      return false;
    } finally {
      writeCoordinator.unlock();
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
      BigDecimal similarity, int attemptCount, long elapsedMillis, Long clearedAtMillis) {
    long similarityComponent = similarity.multiply(BigDecimal.TEN).longValue() * 1_000_000_000_000L;

    if (clearedAtMillis != null && similarity.compareTo(GameConstants.PERFECT_SIMILARITY) >= 0) {
      return similarityComponent - clearedAtMillis;
    }

    long attemptComponent = (long) Math.min(attemptCount, ATTEMPT_COUNT_SCORE_CAP) * 100_000_000L;
    return similarityComponent - attemptComponent - elapsedMillis;
  }

  private Long calculateClearedAtMillis(GameSession session, ZonedDateTime startOfDay) {
    var achieved =
        session.getRankingAchievedAt() == null
            ? session.getClearedAt()
            : session.getRankingAchievedAt();
    if (achieved == null) {
      return null;
    }
    long millis = Duration.between(startOfDay.toInstant(), achieved).toMillis();
    return rankingElapsedMillis(millis);
  }

  private long rankingElapsedMillis(long elapsed) {
    // 과거 이력 없는 세션의 수정 시각이 날짜 밖이어도 시간 성분이 시도 횟수를 침범하지 않습니다.
    return Math.min(MAX_RANKING_ELAPSED_MILLIS, Math.max(0, elapsed));
  }

  private void unavailable(LocalDate date, RuntimeException e) {
    if (e instanceof DataAccessException) {
      recordCacheFailure(date);
    } else {
      markRecoveryNeeded(date);
    }
  }

  private Map<String, String> detail(GameSession session, ZonedDateTime start) {
    Member member = session.getMember();
    return Map.of(
        "publicId", member.getPublicId().toString(),
        "nickname", member.getNickname(),
        "profileUrl", Objects.toString(member.getProfileUrl(), ""),
        "similarity", session.getBestSimilarity().toPlainString(),
        "attemptCount", String.valueOf(rankingAttempts(session)),
        "elapsedMillis", String.valueOf(elapsedMillis(session, start)));
  }
}
