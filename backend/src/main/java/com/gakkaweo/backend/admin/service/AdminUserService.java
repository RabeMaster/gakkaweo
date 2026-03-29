package com.gakkaweo.backend.admin.service;

import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_DETAIL_PREFIX;
import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_KEY_PREFIX;
import static com.gakkaweo.backend.common.redis.RedisKeyConstants.RANKING_MEMBER_PREFIX;
import static com.gakkaweo.backend.common.time.TimeConstants.KST;

import com.gakkaweo.backend.admin.dto.AdminUserResponse;
import com.gakkaweo.backend.admin.dto.ForceNicknameRequest;
import com.gakkaweo.backend.admin.dto.RoleChangeRequest;
import com.gakkaweo.backend.admin.dto.UserDetailResponse;
import com.gakkaweo.backend.admin.dto.UserDetailResponse.ActivitySummary;
import com.gakkaweo.backend.admin.dto.UserGameHistoryResponse;
import com.gakkaweo.backend.admin.dto.UserGameHistoryResponse.GameHistoryEntry;
import com.gakkaweo.backend.admin.dto.UserListResponse;
import com.gakkaweo.backend.auth.service.ProfileImageService;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.admin.repository.SentenceUploadRepository;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.entity.MemberRole;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.repository.SocialAccountRepository;
import com.gakkaweo.backend.domain.member.validation.NicknameValidator;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminUserService {

  private final MemberRepository memberRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final LocalAccountRepository localAccountRepository;
  private final GameSessionRepository gameSessionRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final SentenceUploadRepository sentenceUploadRepository;
  private final ProfileImageService profileImageService;
  private final NicknameValidator nicknameValidator;
  private final StringRedisTemplate redisTemplate;
  private final ApplicationEventPublisher eventPublisher;

  private static Specification<Member> memberFilters(String nickname, Boolean banned) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (nickname != null && !nickname.isBlank()) {
        predicates.add(
            cb.like(cb.lower(root.get("nickname")), "%" + nickname.toLowerCase(Locale.ROOT) + "%"));
      }
      if (banned != null) {
        predicates.add(cb.equal(root.get("banned"), banned));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @Transactional(readOnly = true)
  public UserListResponse getUsers(String nickname, Boolean banned, int page, int size) {
    Page<Member> members =
        memberRepository.findAll(
            memberFilters(nickname, banned),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    List<AdminUserResponse> users =
        members.getContent().stream().map(this::toUserResponse).toList();
    return new UserListResponse(
        users,
        members.getNumber(),
        members.getSize(),
        members.getTotalElements(),
        members.getTotalPages());
  }

  @Transactional(readOnly = true)
  public UserDetailResponse getUserDetail(UUID publicId) {
    Member member = findMember(publicId);

    long totalParticipations = gameSessionRepository.countByMember(member);
    long totalClears = gameSessionRepository.countClearedByMember(member);
    double avgAttemptCount = gameSessionRepository.avgAttemptCountByMember(member);
    Integer bestRank = gameSessionRepository.bestRankByMember(member);

    ActivitySummary activity =
        new ActivitySummary(totalParticipations, totalClears, avgAttemptCount, bestRank);

    var accountInfo = getAccountInfo(member);

    return UserDetailResponse.from(member, accountInfo.provider(), accountInfo.email(), activity);
  }

  @Transactional(readOnly = true)
  public UserGameHistoryResponse getUserHistory(UUID publicId) {
    Member member = findMember(publicId);

    List<GameSession> sessions = gameSessionRepository.findByMemberWithSentence(member);
    List<GameHistoryEntry> history = sessions.stream().map(GameHistoryEntry::from).toList();

    return new UserGameHistoryResponse(history);
  }

  @Transactional
  public AdminUserResponse changeRole(
      UUID targetPublicId, UUID adminPublicId, RoleChangeRequest request) {
    preventSelfAction(targetPublicId, adminPublicId);

    Member member = findMember(targetPublicId);
    MemberRole newRole;
    try {
      newRole = MemberRole.valueOf(request.role());
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    if (member.getRole() == newRole) {
      throw new BusinessException(ErrorCode.ROLE_ALREADY_ASSIGNED);
    }

    member.setRole(newRole);
    return toUserResponse(member);
  }

  @Transactional
  public void banUser(UUID targetPublicId, UUID adminPublicId) {
    preventSelfAction(targetPublicId, adminPublicId);

    Member member = findMember(targetPublicId);
    Long memberId = member.getId();

    // clearAutomatically=true로 영속성 컨텍스트 초기화됨 → delete 먼저, 재조회 후 set
    refreshTokenRepository.deleteByMemberId(memberId);

    Member reloaded = findMember(targetPublicId);
    reloaded.setBanned(true);
    reloaded.setBannedAt(Instant.now());

    log.info("사용자 차단: publicId={}", targetPublicId);
  }

  @Transactional
  public void unbanUser(UUID targetPublicId, UUID adminPublicId) {
    preventSelfAction(targetPublicId, adminPublicId);

    Member member = findMember(targetPublicId);
    member.setBanned(false);
    member.setBannedAt(null);

    log.info("사용자 차단 해제: publicId={}", targetPublicId);
  }

  @Transactional
  public void forceDeleteUser(UUID targetPublicId, UUID adminPublicId) {
    preventSelfAction(targetPublicId, adminPublicId);

    Member member = findMember(targetPublicId);

    gameSessionRepository.anonymizeByMember(member);
    sentenceUploadRepository.anonymizeByAdmin(member);
    localAccountRepository.deleteByMember(member);
    socialAccountRepository.deleteByMember(member);
    refreshTokenRepository.deleteByMemberId(member.getId());
    memberRepository.delete(member);

    log.info("관리자 강제 탈퇴: publicId={}", targetPublicId);
  }

  public void cleanupRedisAfterDelete(UUID publicId) {
    try {
      LocalDate today = LocalDate.now(KST);
      String rankingKey = RANKING_KEY_PREFIX + today;
      String memberKey = RANKING_MEMBER_PREFIX + publicId;
      String detailKey = RANKING_DETAIL_PREFIX + today + ":" + RANKING_MEMBER_PREFIX + publicId;

      Long removed = redisTemplate.opsForZSet().remove(rankingKey, memberKey);
      if (removed != null && removed > 0) {
        redisTemplate.delete(detailKey);
        eventPublisher.publishEvent(new RankingUpdateEvent());
      }
    } catch (Exception e) {
      log.warn("강제 탈퇴 Redis 정리 실패: publicId={}", publicId, e);
    }
  }

  public void cleanupProfileImage(UUID publicId) {
    profileImageService.delete(publicId);
  }

  @Transactional
  public AdminUserResponse forceChangeNickname(
      UUID targetPublicId, UUID adminPublicId, ForceNicknameRequest request) {
    preventSelfAction(targetPublicId, adminPublicId);

    Member member = findMember(targetPublicId);
    String nickname = nicknameValidator.normalize(request.nickname());
    nicknameValidator.validate(nickname);

    if (memberRepository.existsByNickname(nickname)) {
      throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
    }

    member.setNickname(nickname);

    try {
      memberRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
    }

    return toUserResponse(member);
  }

  public void syncNicknameToRedis(UUID publicId, String nickname) {
    try {
      LocalDate today = LocalDate.now(KST);
      String detailKey = RANKING_DETAIL_PREFIX + today + ":" + RANKING_MEMBER_PREFIX + publicId;

      if (redisTemplate.hasKey(detailKey)) {
        redisTemplate.opsForHash().put(detailKey, "nickname", nickname);
        eventPublisher.publishEvent(new RankingUpdateEvent());
      }
    } catch (Exception e) {
      log.warn("닉네임 Redis 동기화 실패: publicId={}", publicId, e);
    }
  }

  @Transactional
  public void forceDeleteProfileImage(UUID targetPublicId, UUID adminPublicId) {
    preventSelfAction(targetPublicId, adminPublicId);

    Member member = findMember(targetPublicId);
    member.setProfileUrl(null);
  }

  public void cleanupProfileImageAndRedis(UUID publicId) {
    profileImageService.delete(publicId);
    syncProfileUrlToRedis(publicId, null);
  }

  private void syncProfileUrlToRedis(UUID publicId, String profileUrl) {
    try {
      LocalDate today = LocalDate.now(KST);
      String detailKey = RANKING_DETAIL_PREFIX + today + ":" + RANKING_MEMBER_PREFIX + publicId;

      if (redisTemplate.hasKey(detailKey)) {
        redisTemplate.opsForHash().put(detailKey, "profileUrl", Objects.toString(profileUrl, ""));
        eventPublisher.publishEvent(new RankingUpdateEvent());
      }
    } catch (Exception e) {
      log.warn("프로필 URL Redis 동기화 실패: publicId={}", publicId, e);
    }
  }

  private Member findMember(UUID publicId) {
    return memberRepository
        .findByPublicId(publicId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
  }

  private void preventSelfAction(UUID targetPublicId, UUID adminPublicId) {
    if (targetPublicId.equals(adminPublicId)) {
      throw new BusinessException(ErrorCode.ADMIN_SELF_ACTION);
    }
  }

  private AdminUserResponse toUserResponse(Member member) {
    var accountInfo = getAccountInfo(member);
    return AdminUserResponse.from(member, accountInfo.provider(), accountInfo.email());
  }

  private AccountInfo getAccountInfo(Member member) {
    return socialAccountRepository
        .findFirstByMember(member)
        .map(sa -> new AccountInfo(sa.getProvider().name(), sa.getEmail()))
        .orElseGet(
            () ->
                new AccountInfo(
                    localAccountRepository.existsByMember(member) ? "LOCAL" : "UNKNOWN", null));
  }

  private record AccountInfo(String provider, String email) {}
}
