package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.admin.service.AdminAuditService;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.admin.entity.AuditAction;
import com.gakkaweo.backend.domain.admin.entity.AuditLog;
import com.gakkaweo.backend.domain.admin.entity.AuditTargetType;
import com.gakkaweo.backend.domain.admin.repository.AuditLogRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("AdminAuditService 오버로드 테스트")
class AdminAuditServiceTest extends IntegrationTestBase {

  @Autowired AdminAuditService adminAuditService;
  @Autowired AuditLogRepository auditLogRepository;

  @Test
  @DisplayName("log(Member, ...) - 이미 조회된 Member 직접 호출")
  void log_member_직접호출() {
    Member admin = testAuthHelper.createAdmin();

    adminAuditService.log(admin, AuditAction.SENTENCE_CREATE, "t-1", "detail", "127.0.0.1");

    AuditLog saved = auditLogRepository.findAll().get(0);
    assertThat(saved.getAction()).isEqualTo(AuditAction.SENTENCE_CREATE);
    assertThat(saved.getTargetType()).isEqualTo(AuditTargetType.SENTENCE);
    assertThat(saved.getTargetId()).isEqualTo("t-1");
    assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
  }

  @Test
  @DisplayName("log(UUID, ...) - 존재하지 않는 publicId MEMBER_NOT_FOUND")
  void log_publicId_미존재() {
    UUID unknown = UUID.randomUUID();

    assertThatThrownBy(
            () -> adminAuditService.log(unknown, AuditAction.ROLE_CHANGE, "t-1", null, "127.0.0.1"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
  }

  @Test
  @DisplayName("log(UUID, ...) - 유효한 adminPublicId는 Member 조회 후 저장")
  void log_publicId_유효() {
    Member admin = testAuthHelper.createAdmin();

    adminAuditService.log(
        admin.getPublicId(), AuditAction.RANKING_CACHE_RESET, "t-1", null, "127.0.0.1");

    assertThat(auditLogRepository.findAll())
        .extracting(AuditLog::getAction)
        .contains(AuditAction.RANKING_CACHE_RESET);
  }
}
