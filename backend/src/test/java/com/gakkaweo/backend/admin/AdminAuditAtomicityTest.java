package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.gakkaweo.backend.admin.dto.RoleChangeRequest;
import com.gakkaweo.backend.admin.dto.SentenceCreateRequest;
import com.gakkaweo.backend.admin.service.AdminAuditService;
import com.gakkaweo.backend.domain.admin.entity.AuditAction;
import com.gakkaweo.backend.domain.admin.entity.AuditLog;
import com.gakkaweo.backend.domain.admin.repository.AuditLogRepository;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.entity.MemberRole;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("Admin 감사 로그 원자성 회귀 가드")
class AdminAuditAtomicityTest extends IntegrationTestBase {

  @MockitoSpyBean AdminAuditService adminAuditService;
  @Autowired DailySentenceRepository dailySentenceRepository;
  @Autowired MemberRepository memberRepository;
  @Autowired AuditLogRepository auditLogRepository;
  @Autowired TransactionTemplate transactionTemplate;

  @Test
  @DisplayName("문장 등록 중 audit 실패 시 DailySentence 쓰기가 롤백된다")
  void 문장등록_audit실패_롤백() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);

    doThrow(new RuntimeException("audit failure"))
        .when(adminAuditService)
        .log(
            any(UUID.class),
            eq(AuditAction.SENTENCE_CREATE),
            anyString(),
            anyString(),
            anyString());

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/sentences"),
            HttpMethod.POST,
            new HttpEntity<>(new SentenceCreateRequest("원자성 테스트 문장"), headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(dailySentenceRepository.existsBySentence("원자성 테스트 문장")).isFalse();
    assertThat(auditRowsByAction(AuditAction.SENTENCE_CREATE)).isZero();
  }

  @Test
  @DisplayName("역할 변경 중 audit 실패 시 Member.role 변경이 롤백된다")
  void 역할변경_audit실패_롤백() {
    Member admin = testAuthHelper.createSuperAdmin();
    Member target = testAuthHelper.createMember();
    HttpHeaders headers = authedJson(admin);

    doThrow(new RuntimeException("audit failure"))
        .when(adminAuditService)
        .log(any(UUID.class), eq(AuditAction.ROLE_CHANGE), anyString(), anyString(), anyString());

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId() + "/role"),
            HttpMethod.PATCH,
            new HttpEntity<>(new RoleChangeRequest("ADMIN"), headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    Member reloaded = memberRepository.findByPublicId(target.getPublicId()).orElseThrow();
    assertThat(reloaded.getRole()).isEqualTo(MemberRole.USER);
    assertThat(auditRowsByAction(AuditAction.ROLE_CHANGE)).isZero();
  }

  @Test
  @DisplayName("외부 트랜잭션 롤백 시 감사 로그는 REQUIRES_NEW로 독립 커밋되어 유지된다")
  void 외부TX_롤백_감사로그_REQUIRES_NEW_유지() {
    Member admin = testAuthHelper.createAdmin();

    try {
      transactionTemplate.execute(
          status -> {
            adminAuditService.log(
                admin, AuditAction.SENTENCE_CREATE, "t-1", "REQUIRES_NEW 검증", "127.0.0.1");
            throw new RuntimeException("outer TX rollback");
          });
    } catch (RuntimeException ignored) {
    }

    assertThat(auditRowsByAction(AuditAction.SENTENCE_CREATE)).isOne();
  }

  private long auditRowsByAction(AuditAction action) {
    return auditLogRepository.findAll().stream()
        .map(AuditLog::getAction)
        .filter(action::equals)
        .count();
  }

  private HttpHeaders authedJson(Member admin) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
