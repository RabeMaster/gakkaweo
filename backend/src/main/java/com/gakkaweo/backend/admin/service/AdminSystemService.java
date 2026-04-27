package com.gakkaweo.backend.admin.service;

import com.gakkaweo.backend.admin.dto.AnnouncementCreateRequest;
import com.gakkaweo.backend.admin.dto.AnnouncementResponse;
import com.gakkaweo.backend.admin.dto.AnnouncementUpdateRequest;
import com.gakkaweo.backend.admin.dto.AuditLogListResponse;
import com.gakkaweo.backend.admin.dto.AuditLogResponse;
import com.gakkaweo.backend.admin.dto.SystemStatusResponse;
import com.gakkaweo.backend.admin.event.AnnouncementEvent;
import com.gakkaweo.backend.admin.sort.AuditLogSortField;
import com.gakkaweo.backend.admin.sort.SortRequestParser;
import com.gakkaweo.backend.admin.sort.SortSpecBuilder;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.common.redis.RedisKeyConstants;
import com.gakkaweo.backend.domain.admin.entity.Announcement;
import com.gakkaweo.backend.domain.admin.entity.AnnouncementType;
import com.gakkaweo.backend.domain.admin.entity.AuditAction;
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
import java.time.Clock;
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
  private final AdminAuditService adminAuditService;
  private final Clock clock;

  private static Specification<AuditLog> auditLogFilters(
      String action, Instant dateFrom, Instant dateTo) {
    final AuditAction actionEnum;
    if (action != null && !action.isBlank()) {
      try {
        actionEnum = AuditAction.valueOf(action);
      } catch (IllegalArgumentException e) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED);
      }
    } else {
      actionEnum = null;
    }
    return (root, query, cb) -> {
      if (Long.class != query.getResultType()) {
        root.fetch("admin", JoinType.LEFT);
      }
      List<Predicate> predicates = new ArrayList<>();
      if (actionEnum != null) {
        predicates.add(cb.equal(root.get("action"), actionEnum));
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
      AnnouncementCreateRequest request, UUID adminPublicId, String ipAddress) {
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
              adminAuditService.log(
                  admin,
                  AuditAction.ANNOUNCEMENT_CREATE,
                  announcement.getId().toString(),
                  request.title(),
                  ipAddress);
              return AnnouncementResponse.from(announcement);
            });

    if (response != null && isCurrentlyActiveByTime(request.startsAt(), request.endsAt())) {
      eventPublisher.publishEvent(
          new AnnouncementEvent(response.id(), response.title(), response.content(), type));
    }

    return response;
  }

  public AnnouncementResponse updateAnnouncement(
      Long id, AnnouncementUpdateRequest request, UUID adminPublicId, String ipAddress) {
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
              adminAuditService.log(
                  adminPublicId, AuditAction.ANNOUNCEMENT_UPDATE, id.toString(), null, ipAddress);
              return AnnouncementResponse.from(announcement);
            });

    if (response != null && response.active()) {
      Instant now = clock.instant();
      if (!response.startsAt().isAfter(now)
          && (response.endsAt() == null || !response.endsAt().isBefore(now))) {
        AnnouncementType type = AnnouncementType.valueOf(response.type());
        eventPublisher.publishEvent(
            new AnnouncementEvent(response.id(), response.title(), response.content(), type));
      }
    }

    return response;
  }

  public void deleteAnnouncement(Long id, UUID adminPublicId, String ipAddress) {
    AnnouncementEvent event =
        transactionTemplate.execute(
            status -> {
              Announcement announcement = findAnnouncement(id);
              AnnouncementEvent ev =
                  new AnnouncementEvent(id, announcement.getTitle(), null, announcement.getType());
              announcementRepository.delete(announcement);
              adminAuditService.log(
                  adminPublicId, AuditAction.ANNOUNCEMENT_DELETE, id.toString(), null, ipAddress);
              return ev;
            });
    eventPublisher.publishEvent(event);
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

  public void resetRankingCache(UUID adminPublicId, String ipAddress) {
    LocalDate today = LocalDate.now(clock);
    String rankingKey = RedisKeyConstants.rankingKey(today);

    Set<String> members = redisTemplate.opsForZSet().range(rankingKey, 0, -1);
    if (members != null) {
      for (String memberKey : members) {
        UUID publicId = RedisKeyConstants.extractMemberPublicId(memberKey);
        String detailKey = RedisKeyConstants.rankingDetailKey(today, publicId);
        redisTemplate.delete(detailKey);
      }
    }
    redisTemplate.delete(rankingKey);

    int rebuilt = rankingService.rebuildRankingCache(today);
    eventPublisher.publishEvent(new RankingUpdateEvent());
    log.info("랭킹 캐시 리셋 및 재구축: date={}, rebuilt={}", today, rebuilt);
    adminAuditService.log(adminPublicId, AuditAction.RANKING_CACHE_RESET, null, null, ipAddress);
  }

  public void resetRateLimit(UUID adminPublicId, String ipAddress) {
    bucketStore.clearAllBuckets();
    log.info("Rate limit 버킷 전체 초기화");
    adminAuditService.log(adminPublicId, AuditAction.RATE_LIMIT_RESET, null, null, ipAddress);
  }

  @Transactional(readOnly = true)
  public AuditLogListResponse getAuditLogs(
      String action, Instant dateFrom, Instant dateTo, String sort, int page, int size) {
    SortRequestParser.SortSpec sortSpec =
        SortRequestParser.parse(
            sort, AuditLogSortField.class, AuditLogSortField.CREATED_AT, Sort.Direction.DESC);
    Specification<AuditLog> spec =
        auditLogFilters(action, dateFrom, dateTo).and(SortSpecBuilder.build(sortSpec, "id"));
    Page<AuditLog> pageResult = auditLogRepository.findAll(spec, PageRequest.of(page, size));

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
    Instant now = clock.instant();
    if (startsAt.isAfter(now)) {
      return false;
    }
    return endsAt == null || !endsAt.isBefore(now);
  }
}
