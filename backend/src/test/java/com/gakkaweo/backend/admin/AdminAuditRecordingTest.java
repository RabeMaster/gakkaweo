package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.RoleChangeRequest;
import com.gakkaweo.backend.admin.dto.SentenceCreateRequest;
import com.gakkaweo.backend.domain.admin.entity.AuditAction;
import com.gakkaweo.backend.domain.admin.entity.AuditLog;
import com.gakkaweo.backend.domain.admin.entity.AuditTargetType;
import com.gakkaweo.backend.domain.admin.repository.AuditLogRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

@DisplayName("Admin 감사 로그 기록 통합 테스트")
class AdminAuditRecordingTest extends IntegrationTestBase {

  @Autowired AuditLogRepository auditLogRepository;

  @Test
  @DisplayName("역할 변경 → ROLE_CHANGE 기록")
  void 역할변경_감사() {
    Member admin = testAuthHelper.createSuperAdmin();
    Member target = testAuthHelper.createMember();
    HttpHeaders headers = authedJson(admin);

    restTemplate.exchange(
        url("/admin/users/" + target.getPublicId() + "/role"),
        HttpMethod.PATCH,
        new HttpEntity<>(new RoleChangeRequest("ADMIN"), headers),
        Void.class);

    AuditLog logged = assertSingleLog(AuditAction.ROLE_CHANGE);
    assertThat(logged.getTargetType()).isEqualTo(AuditTargetType.MEMBER);
    assertThat(logged.getTargetId()).isEqualTo(target.getPublicId().toString());
    assertThat(logged.getDetail()).contains("ADMIN");
  }

  @Test
  @DisplayName("차단 → USER_BAN 기록")
  void 차단_감사() {
    Member admin = testAuthHelper.createAdmin();
    Member target = testAuthHelper.createMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    restTemplate.exchange(
        url("/admin/users/" + target.getPublicId() + "/ban"),
        HttpMethod.POST,
        new HttpEntity<>(headers),
        Void.class);

    AuditLog logged = assertSingleLog(AuditAction.USER_BAN);
    assertThat(logged.getTargetId()).isEqualTo(target.getPublicId().toString());
  }

  @Test
  @DisplayName("문장 등록 → SENTENCE_CREATE 기록")
  void 문장등록_감사() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);

    restTemplate.exchange(
        url("/admin/sentences"),
        HttpMethod.POST,
        new HttpEntity<>(new SentenceCreateRequest("테스트 문장"), headers),
        Void.class);

    AuditLog logged = assertSingleLog(AuditAction.SENTENCE_CREATE);
    assertThat(logged.getTargetType()).isEqualTo(AuditTargetType.SENTENCE);
    assertThat(logged.getDetail()).isEqualTo("테스트 문장");
  }

  @Test
  @DisplayName("랭킹 캐시 리셋 → RANKING_CACHE_RESET 기록")
  void 랭킹리셋_감사() {
    testAuthHelper.createTodaySentence("오늘 문장");
    Member admin = testAuthHelper.createSuperAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    restTemplate.exchange(
        url("/admin/system/ranking-cache/reset"),
        HttpMethod.POST,
        new HttpEntity<>(headers),
        Void.class);

    AuditLog logged = assertSingleLog(AuditAction.RANKING_CACHE_RESET);
    assertThat(logged.getTargetType()).isEqualTo(AuditTargetType.SYSTEM);
  }

  private AuditLog assertSingleLog(AuditAction action) {
    List<AuditLog> logs =
        auditLogRepository.findAll().stream().filter(l -> action == l.getAction()).toList();
    assertThat(logs).hasSize(1);
    return logs.get(0);
  }

  private HttpHeaders authedJson(Member admin) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
