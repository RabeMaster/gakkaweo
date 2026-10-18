package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.auth.config.JwtProperties;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.jwt.JwtProvider;
import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.common.util.HashUtils;
import com.gakkaweo.backend.domain.auth.entity.AuthSession;
import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import com.gakkaweo.backend.domain.auth.repository.AuthSessionRepository;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 로그인 세션의 발급·갱신·폐기와 회원 잠금 순서를 관리합니다. */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthSessionService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthSessionRepository authSessionRepository;
  private final MemberRepository memberRepository;
  private final JwtProvider jwtProvider;
  private final JwtProperties jwtProperties;
  private final Clock clock;
  private final TransactionTemplate newTxTemplate;

  @Transactional(readOnly = true)
  public CustomUserDetails authenticate(UUID sessionPublicId, UUID publicId, String role) {
    var state =
        authSessionRepository.findAuthenticationStateByPublicId(sessionPublicId).orElse(null);

    if (state == null || !state.getPublicId().equals(publicId)) {
      return null;
    }

    if (Boolean.TRUE.equals(state.getBanned())) {
      throw new BusinessException(ErrorCode.MEMBER_BANNED);
    }

    if (state.isRevoked()
        || !state.getExpiresAt().isAfter(clock.instant())
        || !state.getRole().name().equals(role)) {
      return null;
    }

    return new CustomUserDetails(publicId, state.getRole().name());
  }

  @Transactional
  public TokenPair issue(Long memberId) {
    Member member = lockMember(memberId);

    if (Boolean.TRUE.equals(member.getBanned())) {
      throw new BusinessException(ErrorCode.MEMBER_BANNED);
    }

    AuthSession session = authSessionRepository.save(new AuthSession(member, refreshExpiresAt()));
    return issueTokens(session);
  }

  public TokenPair rotate(String rawToken) {
    record RotationResult(TokenPair tokens, ErrorCode error) {}

    RotationResult result =
        newTxTemplate.execute(
            status -> {
              String hash = hashToken(rawToken);

              var reference =
                  refreshTokenRepository
                      .findReferenceByTokenHash(hash)
                      .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

              // 발급·갱신·폐기는 모두 회원 행을 먼저 잠급니다.
              if (reference.getSessionId() == null) {
                throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
              }

              Member member = lockMember(reference.getMemberId());

              AuthSession session =
                  authSessionRepository
                      .findByIdForUpdate(reference.getSessionId())
                      .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
              RefreshToken existing =
                  refreshTokenRepository
                      .findByTokenHashForUpdate(hash)
                      .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

              if (Boolean.TRUE.equals(member.getBanned())) {
                revoke(session);
                return new RotationResult(null, ErrorCode.MEMBER_BANNED);
              }

              if (existing.isRevoked() || session.isRevoked()) {
                revoke(session);
                return new RotationResult(null, ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
              }

              if (!existing.getExpiresAt().isAfter(clock.instant())) {
                revoke(session);
                return new RotationResult(null, ErrorCode.EXPIRED_TOKEN);
              }

              existing.setRevoked(true);
              session.extend(refreshExpiresAt());
              return new RotationResult(issueTokens(session), null);
            });

    // 재사용을 발견한 세션의 폐기를 커밋한 다음 오류를 반환합니다.
    if (result.error() != null) {
      throw new BusinessException(result.error());
    }

    return result.tokens();
  }

  @Transactional
  public void logout(String accessToken, String refreshToken) {
    var accessReference = parseLogoutReference(accessToken);

    if (accessReference != null) {
      Member member =
          memberRepository.findByPublicIdForUpdate(accessReference.owner()).orElse(null);

      if (member == null) {
        return;
      }

      AuthSession session =
          authSessionRepository
              .findByPublicIdForUpdate(accessReference.sessionPublicId())
              .orElse(null);
      revokeOwnedSession(member, session);
      return;
    }

    if (refreshToken == null) {
      return;
    }

    var reference =
        refreshTokenRepository.findReferenceByTokenHash(hashToken(refreshToken)).orElse(null);

    if (reference == null || reference.getSessionId() == null) {
      return;
    }

    Member member = memberRepository.findByIdForUpdate(reference.getMemberId()).orElse(null);

    if (member == null) {
      return;
    }

    AuthSession session =
        authSessionRepository.findByIdForUpdate(reference.getSessionId()).orElse(null);
    revokeOwnedSession(member, session);
  }

  private LogoutReference parseLogoutReference(String accessToken) {
    if (accessToken == null) {
      return null;
    }

    try {
      Claims claims = jwtProvider.parseAccessToken(accessToken);

      String sid = claims.get("sid", String.class);

      if (sid == null) {
        return null;
      }

      return new LogoutReference(UUID.fromString(claims.getSubject()), UUID.fromString(sid));
    } catch (JwtException | IllegalArgumentException e) {
      // 액세스 토큰이 만료되어도 갱신 토큰으로 현재 로그인을 폐기할 수 있습니다.
    }

    return null;
  }

  private void revokeOwnedSession(Member member, AuthSession session) {
    if (session == null || !session.getMember().getId().equals(member.getId())) {
      return;
    }

    revoke(session);
  }

  @Transactional
  public void logoutAll(UUID publicId) {
    Member member =
        memberRepository
            .findByPublicIdForUpdate(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    revokeMemberSessions(member);
  }

  @Transactional
  public void revokeAll(Long memberId) {
    revokeMemberSessions(lockMember(memberId));
  }

  @Transactional
  public void deleteAll(Long memberId) {
    Member member = lockMember(memberId);

    revokeMemberSessions(member);
    refreshTokenRepository.deleteByMemberId(memberId);
    authSessionRepository.deleteByMemberId(memberId);
  }

  @Scheduled(fixedDelay = 3600000)
  public void discardExpired() {
    Instant now = clock.instant();

    var memberIds =
        newTxTemplate.execute(
            status ->
                Stream.concat(
                        authSessionRepository.findExpiredMemberIds(now).stream(),
                        refreshTokenRepository.findExpiredMemberIds(now).stream())
                    .distinct()
                    .toList());

    for (Long memberId : memberIds) {
      // 갱신·폐기와 같은 회원 잠금을 먼저 잡고, 한 회원씩 별도 트랜잭션에서 정리합니다.
      newTxTemplate.executeWithoutResult(
          status ->
              memberRepository
                  .findByIdForUpdate(memberId)
                  .ifPresent(
                      member -> {
                        refreshTokenRepository.deleteExpiredByMemberId(memberId, now);
                        authSessionRepository.deleteExpiredByMemberId(memberId, now);
                      }));
    }
  }

  private Member lockMember(Long memberId) {
    return memberRepository
        .findByIdForUpdate(memberId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
  }

  private Instant refreshExpiresAt() {
    return clock.instant().plus(jwtProperties.refreshExpiration());
  }

  private TokenPair issueTokens(AuthSession session) {
    String rawToken = UUID.randomUUID().toString();

    refreshTokenRepository.save(
        new RefreshToken(session, hashToken(rawToken), session.getExpiresAt()));

    String accessToken =
        jwtProvider.createAccessToken(
            session.getMember().getPublicId(),
            session.getMember().getRole().name(),
            session.getPublicId());

    return new TokenPair(accessToken, rawToken);
  }

  private void revokeMemberSessions(Member member) {
    authSessionRepository.findByMemberId(member.getId()).forEach(this::revoke);
  }

  private void revoke(AuthSession session) {
    session.revoke();
    refreshTokenRepository
        .findBySessionId(session.getId())
        .forEach(token -> token.setRevoked(true));

    log.info("로그인 세션 폐기: sessionId={}", session.getId());
  }

  private String hashToken(String rawToken) {
    return HashUtils.sha256Hex(rawToken);
  }

  private record LogoutReference(UUID owner, UUID sessionPublicId) {}
}
