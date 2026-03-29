package com.gakkaweo.backend.admin.service;

import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_KEY_PREFIX;
import static com.gakkaweo.backend.common.time.TimeConstants.KST;

import com.gakkaweo.backend.admin.dto.DuplicateCheckRequest;
import com.gakkaweo.backend.admin.dto.DuplicateCheckResponse;
import com.gakkaweo.backend.admin.dto.EmergencyReplaceRequest;
import com.gakkaweo.backend.admin.dto.ScheduleRequest;
import com.gakkaweo.backend.admin.dto.SentenceCreateRequest;
import com.gakkaweo.backend.admin.dto.SentenceListResponse;
import com.gakkaweo.backend.admin.dto.SentenceResponse;
import com.gakkaweo.backend.admin.dto.SentenceStatsResponse;
import com.gakkaweo.backend.admin.dto.SentenceUpdateRequest;
import com.gakkaweo.backend.admin.dto.SimilarityTestRequest;
import com.gakkaweo.backend.admin.dto.SimilarityTestResponse;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.infra.ai.service.SimilarityService;
import com.gakkaweo.backend.ranking.event.DayChangeEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminSentenceService {

  private final DailySentenceRepository dailySentenceRepository;
  private final GameSessionRepository gameSessionRepository;
  private final SimilarityService similarityService;
  private final StringRedisTemplate redisTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate transactionTemplate;

  @Transactional(readOnly = true)
  public SentenceListResponse getSentences(String status, int page, int size) {
    DailySentenceStatus statusEnum = null;
    if (status != null && !status.isBlank()) {
      try {
        statusEnum = DailySentenceStatus.valueOf(status.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED);
      }
    }

    Page<DailySentence> pageResult =
        dailySentenceRepository.findByStatusFilter(statusEnum, PageRequest.of(page, size));

    return new SentenceListResponse(
        pageResult.getContent().stream().map(SentenceResponse::from).toList(),
        pageResult.getNumber(),
        pageResult.getSize(),
        pageResult.getTotalElements(),
        pageResult.getTotalPages());
  }

  @Transactional
  public SentenceResponse createSentence(SentenceCreateRequest request) {
    String sentence = request.sentence().strip();
    if (dailySentenceRepository.existsBySentence(sentence)) {
      throw new BusinessException(ErrorCode.SENTENCE_DUPLICATE);
    }

    DailySentence entity = new DailySentence(sentence);
    dailySentenceRepository.save(entity);
    log.info("문장 등록: publicId={}", entity.getPublicId());
    return SentenceResponse.from(entity);
  }

  @Transactional(readOnly = true)
  public SentenceResponse getSentence(UUID publicId) {
    DailySentence entity = findByPublicIdOrThrow(publicId);
    return SentenceResponse.from(entity);
  }

  @Transactional
  public SentenceResponse updateSentence(UUID publicId, SentenceUpdateRequest request) {
    DailySentence entity = findByPublicIdOrThrow(publicId);

    if (entity.getUsedAt() != null) {
      throw new BusinessException(ErrorCode.SENTENCE_ALREADY_USED);
    }

    String newSentence = request.sentence().strip();
    if (!entity.getSentence().equals(newSentence)
        && dailySentenceRepository.existsBySentence(newSentence)) {
      throw new BusinessException(ErrorCode.SENTENCE_DUPLICATE);
    }

    entity.setSentence(newSentence);
    return SentenceResponse.from(entity);
  }

  @Transactional
  public void deleteSentence(UUID publicId) {
    DailySentence entity = findByPublicIdOrThrow(publicId);

    if (entity.getUsedAt() != null) {
      throw new BusinessException(ErrorCode.SENTENCE_ALREADY_USED);
    }

    dailySentenceRepository.delete(entity);
    log.info("문장 삭제: publicId={}", publicId);
  }

  @Transactional(readOnly = true)
  public SentenceStatsResponse getSentenceStats(UUID publicId) {
    DailySentence entity = findByPublicIdOrThrow(publicId);

    long totalSessions = gameSessionRepository.countBySentence(entity);
    long clearedSessions = gameSessionRepository.countClearedBySentence(entity);
    double clearRate = totalSessions > 0 ? (double) clearedSessions / totalSessions * 100 : 0;
    BigDecimal avgSimilarity = gameSessionRepository.avgSimilarityBySentence(entity);
    double avgAttemptCount = gameSessionRepository.avgAttemptCountBySentence(entity);

    return new SentenceStatsResponse(
        totalSessions, clearedSessions, clearRate, avgSimilarity, avgAttemptCount);
  }

  @Transactional(readOnly = true)
  public long getUnusedCount() {
    return dailySentenceRepository.countUnusedActive();
  }

  @Transactional
  public SentenceResponse schedule(UUID publicId, ScheduleRequest request) {
    DailySentence entity = findByPublicIdOrThrow(publicId);

    if (entity.getUsedAt() != null) {
      throw new BusinessException(ErrorCode.SENTENCE_ALREADY_USED);
    }

    if (dailySentenceRepository.existsByScheduledAt(request.date())
        && (entity.getScheduledAt() == null || !entity.getScheduledAt().equals(request.date()))) {
      throw new BusinessException(ErrorCode.SENTENCE_ALREADY_SCHEDULED);
    }

    entity.setScheduledAt(request.date());
    return SentenceResponse.from(entity);
  }

  @Transactional
  public SentenceResponse unschedule(UUID publicId) {
    DailySentence entity = findByPublicIdOrThrow(publicId);
    entity.setScheduledAt(null);
    return SentenceResponse.from(entity);
  }

  public SimilarityTestResponse testSimilarity(SimilarityTestRequest request) {
    BigDecimal score = similarityService.testSimilarity(request.sentence(), request.guessText());
    return new SimilarityTestResponse(request.sentence(), request.guessText(), score);
  }

  public DuplicateCheckResponse checkDuplicate(DuplicateCheckRequest request) {
    List<String> existingSentences =
        dailySentenceRepository.findAll().stream().map(DailySentence::getSentence).toList();

    List<DuplicateCheckResponse.SimilarEntry> similarEntries = new ArrayList<>();
    BigDecimal threshold = new BigDecimal("80.0");

    for (String existing : existingSentences) {
      try {
        BigDecimal score = similarityService.testSimilarity(existing, request.sentence());
        if (score.compareTo(threshold) >= 0) {
          similarEntries.add(new DuplicateCheckResponse.SimilarEntry(existing, score));
        }
      } catch (Exception e) {
        log.warn("유사도 검사 실패: sentence={}", existing, e);
      }
    }

    return new DuplicateCheckResponse(!similarEntries.isEmpty(), similarEntries);
  }

  public SentenceResponse emergencyReplace(EmergencyReplaceRequest request) {
    LocalDate today = LocalDate.now(KST);

    UUID newPublicId =
        transactionTemplate.execute(
            status -> {
              DailySentence currentSentence =
                  dailySentenceRepository
                      .findByUsedAt(today)
                      .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));

              DailySentence newSentence = findByPublicIdOrThrow(request.newSentencePublicId());

              if (newSentence.getUsedAt() != null) {
                throw new BusinessException(ErrorCode.SENTENCE_ALREADY_USED);
              }
              if (newSentence.getStatus() != DailySentenceStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.SENTENCE_NOT_FOUND);
              }

              gameSessionRepository.expireInProgressSessions(currentSentence);

              currentSentence.setUsedAt(null);
              if (!request.returnOldToPool()) {
                currentSentence.setStatus(DailySentenceStatus.DISABLED);
              }

              newSentence.setUsedAt(today);
              newSentence.setScheduledAt(null);

              log.info(
                  "긴급 교체 완료: old={}, new={}, returnToPool={}",
                  currentSentence.getPublicId(),
                  newSentence.getPublicId(),
                  request.returnOldToPool());

              return newSentence.getPublicId();
            });

    String rankingKey = RANKING_KEY_PREFIX + today;
    redisTemplate.delete(rankingKey);

    eventPublisher.publishEvent(new DayChangeEvent(newPublicId));

    return getSentence(newPublicId);
  }

  private DailySentence findByPublicIdOrThrow(UUID publicId) {
    return dailySentenceRepository
        .findByPublicId(publicId)
        .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));
  }
}
