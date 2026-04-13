package com.gakkaweo.backend.admin.service;

import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_DETAIL_PREFIX;
import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_KEY_PREFIX;
import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_MEMBER_PREFIX;
import static com.gakkaweo.backend.common.time.TimeConstants.KST;

import com.gakkaweo.backend.admin.dto.AnnouncementCreateRequest;
import com.gakkaweo.backend.admin.dto.AnnouncementResponse;
import com.gakkaweo.backend.admin.dto.AnnouncementUpdateRequest;
import com.gakkaweo.backend.admin.dto.AuditLogListResponse;
import com.gakkaweo.backend.admin.dto.AuditLogResponse;
import com.gakkaweo.backend.admin.dto.SystemStatusResponse;
import com.gakkaweo.backend.admin.event.AnnouncementEvent;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.admin.entity.Announcement;
import com.gakkaweo.backend.domain.admin.entity.AnnouncementType;
import com.gakkaweo.backend.domain.admin.entity.AuditLog;
import com.gakkaweo.backend.domain.admin.repository.AnnouncementRepository;
import com.gakkaweo.backend.domain.admin.repository.AuditLogRepository;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.infra.ai.client.AiServiceClient;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.ranking.sse.SseConnectionManager;
import com.gakkaweo.backend.ratelimit.filter.BucketStore;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.connection.RedisConnectionCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminSystemService {

  private final AnnouncementRepository announcementRepository;
  private final AuditLogRepository auditLogRepository;
  private final MemberRepository memberRepository;
  private final DailySentenceRepository dailySentenceRepository;
  private final AiServiceClient aiServiceClient;
  private final SseConnectionManager sseConnectionManager;
  private final RankingService rankingService;
  private final BucketStore bucketStore;
  private final StringRedisTemplate redisTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate transactionTemplate;

  private static Specification<AuditLog> auditLogFilters(
      String action, Instant dateFrom, Instant dateTo) {
    return (root, query, cb) -> {
      if (Long.class != query.getResultType()) {
        root.fetch("admin", JoinType.LEFT);
      }
      List<Predicate> predicates = new ArrayList<>();
      if (action != null && !action.isBlank()) {
        predicates.add(cb.equal(root.get("action"), action));
      }
      if (dateFrom != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
      }
      if (dateTo != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @Transactional(readOnly = true)
  public List<AnnouncementResponse> getAnnouncements() {
    return announcementRepository.findAllWithAdmin().stream()
        .map(AnnouncementResponse::from)
        .toList();
  }

  public AnnouncementResponse createAnnouncement(
      AnnouncementCreateRequest request, UUID adminPublicId) {
    AnnouncementType type = parseAnnouncementType(request.type());
    validateAnnouncementDates(request.startsAt(), request.endsAt());

    AnnouncementResponse response =
        transactionTemplate.execute(
            status -> {
              Member admin = findMember(adminPublicId);
              Announcement announcement =
                  new Announcement(
                      admin,
                      request.title(),
                      request.content(),
                      type,
                      request.startsAt(),
                      request.endsAt());
              announcementRepository.save(announcement);
              return AnnouncementResponse.from(announcement);
            });

    if (response != null && isCurrentlyActiveByTime(request.startsAt(), request.endsAt())) {
      eventPublisher.publishEvent(
          new AnnouncementEvent(response.id(), response.title(), response.content(), type));
    }

    return response;
  }

  public AnnouncementResponse updateAnnouncement(Long id, AnnouncementUpdateRequest request) {
    if (request.type() != null) {
      parseAnnouncementType(request.type());
    }

    AnnouncementResponse response =
        transactionTemplate.execute(
            status -> {
              Announcement announcement = findAnnouncement(id);

              if (request.title() != null) {
                announcement.setTitle(request.title());
              }
              if (request.content() != null) {
                announcement.setContent(request.content());
              }
              if (request.type() != null) {
                announcement.setType(parseAnnouncementType(request.type()));
              }
              if (request.active() != null) {
                announcement.setActive(request.active());
              }
              if (request.startsAt() != null) {
                announcement.setStartsAt(request.startsAt());
              }
              if (request.endsAt() != null) {
                announcement.setEndsAt(request.endsAt());
              }
              validateAnnouncementDates(announcement.getStartsAt(), announcement.getEndsAt());
              announcementRepository.save(announcement);
              return AnnouncementResponse.from(announcement);
            });

    if (response != null && response.active()) {
      Instant now = Instant.now();
      if (!response.startsAt().isAfter(now)
          && (response.endsAt() == null || !response.endsAt().isBefore(now))) {
        AnnouncementType type = AnnouncementType.valueOf(response.type());
        eventPublisher.publishEvent(
            new AnnouncementEvent(response.id(), response.title(), response.content(), type));
      }
    }

    return response;
  }

  public void deleteAnnouncement(Long id) {
    Announcement announcement = findAnnouncement(id);
    transactionTemplate.executeWithoutResult(status -> announcementRepository.delete(announcement));
    eventPublisher.publishEvent(
        new AnnouncementEvent(id, announcement.getTitle(), null, announcement.getType()));
  }

  public SystemStatusResponse getSystemStatus() {
    int sseCount = sseConnectionManager.getConnectionCount();

    long aiStart = System.currentTimeMillis();
    boolean aiHealthy = aiServiceClient.isHealthy();
    long aiResponseMs = System.currentTimeMillis() - aiStart;

    boolean redisHealthy;
    try {
      String pong = redisTemplate.execute(RedisConnectionCommands::ping);
      redisHealthy = pong != null;
    } catch (Exception e) {
      redisHealthy = false;
    }

    long totalMembers = memberRepository.count();
    long totalSentences = dailySentenceRepository.count();
    long unusedSentences = dailySentenceRepository.countUnusedActive();

    return new SystemStatusResponse(
        sseCount,
        aiHealthy,
        aiResponseMs,
        redisHealthy,
        totalMembers,
        totalSentences,
        unusedSentences);
  }

  public void resetRankingCache() {
    LocalDate today = LocalDate.now(KST);
    String rankingKey = RANKING_KEY_PREFIX + today;

    Set<String> members = redisTemplate.opsForZSet().range(rankingKey, 0, -1);
    if (members != null) {
      for (String memberKey : members) {
        String publicIdStr = memberKey.substring(RANKING_MEMBER_PREFIX.length());
        String detailKey =
            RANKING_DETAIL_PREFIX + today + ":" + RANKING_MEMBER_PREFIX + publicIdStr;
        redisTemplate.delete(detailKey);
      }
    }
    redisTemplate.delete(rankingKey);

    int rebuilt = rankingService.rebuildRankingCache(today);
    eventPublisher.publishEvent(new RankingUpdateEvent());
    log.info("랭킹 캐시 리셋 및 재구축: date={}, rebuilt={}", today, rebuilt);
  }

  public void resetRateLimit() {
    bucketStore.clearAllBuckets();
    log.info("Rate limit 버킷 전체 초기화");
  }

  @Transactional(readOnly = true)
  public AuditLogListResponse getAuditLogs(
      String action, Instant dateFrom, Instant dateTo, int page, int size) {
    Page<AuditLog> pageResult =
        auditLogRepository.findAll(
            auditLogFilters(action, dateFrom, dateTo),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

    return new AuditLogListResponse(
        pageResult.getContent().stream().map(AuditLogResponse::from).toList(),
        pageResult.getNumber(),
        pageResult.getSize(),
        pageResult.getTotalElements(),
        pageResult.getTotalPages());
  }

  private Announcement findAnnouncement(Long id) {
    return announcementRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
  }

  private Member findMember(UUID publicId) {
    return memberRepository
        .findByPublicId(publicId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
  }

  private void validateAnnouncementDates(Instant startsAt, Instant endsAt) {
    if (endsAt != null && endsAt.isBefore(startsAt)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
  }

  private AnnouncementType parseAnnouncementType(String type) {
    try {
      return AnnouncementType.valueOf(type);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
  }

  private boolean isCurrentlyActiveByTime(Instant startsAt, Instant endsAt) {
    Instant now = Instant.now();
    if (startsAt.isAfter(now)) {
      return false;
    }
    return endsAt == null || !endsAt.isBefore(now);
  }
}
