package com.gakkaweo.backend.domain.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.domain.admin.entity.AuditAction;
import com.gakkaweo.backend.domain.admin.entity.AuditLog;
import com.gakkaweo.backend.domain.admin.entity.AuditTargetType;
import com.gakkaweo.backend.domain.admin.repository.AuditLogRepository;
import com.gakkaweo.backend.domain.auth.entity.RefreshToken;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.entity.SocialAccount;
import com.gakkaweo.backend.domain.member.entity.SocialProvider;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.repository.SocialAccountRepository;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("엔티티 Auditing 자동 채움 통합 테스트")
class EntityAuditingIntegrationTest extends IntegrationTestBase {

  @Autowired private MemberRepository memberRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private SocialAccountRepository socialAccountRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @Test
  @DisplayName("BaseTimeEntity 상속체 - createdAt이 Clock instant로 자동 채움")
  void baseTimeEntity_createdAt_자동채움() {
    Instant fixed = Instant.parse("2026-05-01T00:00:00Z");
    ((TestClock) clock).setInstant(fixed);

    Member member = transactionTemplate.execute(s -> memberRepository.save(new Member("토큰소유자")));

    RefreshToken token =
        transactionTemplate.execute(
            s ->
                refreshTokenRepository.save(
                    new RefreshToken(
                        member,
                        "hash-" + UUID.randomUUID(),
                        UUID.randomUUID(),
                        fixed.plusSeconds(3600))));

    RefreshToken loaded = refreshTokenRepository.findById(token.getId()).orElseThrow();
    assertThat(loaded.getCreatedAt()).isEqualTo(fixed);
  }

  @Test
  @DisplayName("BaseAuditableEntity 상속체 - createdAt + updatedAt 동시 채움 후 update 시 updatedAt만 변경")
  void baseAuditableEntity_createdAt_updatedAt_동시채움_그리고_업데이트() {
    Instant created = Instant.parse("2026-05-01T00:00:00Z");
    ((TestClock) clock).setInstant(created);

    Member saved = transactionTemplate.execute(s -> memberRepository.save(new Member("회원1")));

    Member afterInsert = memberRepository.findById(saved.getId()).orElseThrow();
    assertThat(afterInsert.getCreatedAt()).isEqualTo(created);
    assertThat(afterInsert.getUpdatedAt()).isEqualTo(created);

    Instant later = created.plus(Duration.ofMinutes(10));
    ((TestClock) clock).setInstant(later);

    transactionTemplate.executeWithoutResult(
        s -> {
          Member m = memberRepository.findById(saved.getId()).orElseThrow();
          m.setNickname("회원1-수정");
        });

    Member afterUpdate = memberRepository.findById(saved.getId()).orElseThrow();
    assertThat(afterUpdate.getCreatedAt()).isEqualTo(created);
    assertThat(afterUpdate.getUpdatedAt()).isEqualTo(later);
  }

  @Test
  @DisplayName("SocialAccount - connectedAt이 Clock instant로 자동 채움")
  void socialAccount_connectedAt_자동채움() {
    Instant fixed = Instant.parse("2026-05-01T12:34:56Z");
    ((TestClock) clock).setInstant(fixed);

    Member member = transactionTemplate.execute(s -> memberRepository.save(new Member("소셜회원")));

    SocialAccount saved =
        transactionTemplate.execute(
            s ->
                socialAccountRepository.save(
                    new SocialAccount(member, SocialProvider.KAKAO, "kakao-" + UUID.randomUUID())));

    SocialAccount loaded = socialAccountRepository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getConnectedAt()).isEqualTo(fixed);
  }

  @Test
  @DisplayName("AuditLog - createdAt이 Clock instant로 자동 채움 (#150 후속 회귀 가드)")
  void auditLog_createdAt_자동채움() {
    Instant fixed = Instant.parse("2026-05-01T09:00:00Z");
    ((TestClock) clock).setInstant(fixed);

    Member admin = transactionTemplate.execute(s -> memberRepository.save(new Member("운영자")));

    AuditLog saved =
        transactionTemplate.execute(
            s ->
                auditLogRepository.save(
                    new AuditLog(
                        admin,
                        AuditAction.SENTENCE_CREATE,
                        AuditTargetType.SENTENCE,
                        "1",
                        "test-detail",
                        "127.0.0.1")));

    AuditLog loaded = auditLogRepository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getCreatedAt()).isEqualTo(fixed);
  }
}
