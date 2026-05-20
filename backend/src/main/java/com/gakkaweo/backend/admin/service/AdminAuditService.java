package com.gakkaweo.backend.admin.service;

import com.gakkaweo.backend.admin.event.AuditLogEvent;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.admin.entity.AuditAction;
import com.gakkaweo.backend.domain.admin.entity.AuditLog;
import com.gakkaweo.backend.domain.admin.repository.AuditLogRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminAuditService {

  private final AuditLogRepository auditLogRepository;
  private final MemberRepository memberRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void log(
      Member admin, AuditAction action, String targetId, String detail, String ipAddress) {
    doLog(admin, action, targetId, detail, ipAddress);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void log(
      UUID adminPublicId, AuditAction action, String targetId, String detail, String ipAddress) {
    Member admin =
        memberRepository
            .findByPublicId(adminPublicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    doLog(admin, action, targetId, detail, ipAddress);
  }

  private void doLog(
      Member admin, AuditAction action, String targetId, String detail, String ipAddress) {
    AuditLog auditLog =
        new AuditLog(admin, action, action.targetType(), targetId, detail, ipAddress);
    auditLogRepository.save(auditLog);
    log.info(
        "감사 로그: admin={}, action={}, targetType={}, targetId={}",
        admin.getNickname(),
        action,
        action.targetType(),
        targetId);
    eventPublisher.publishEvent(
        new AuditLogEvent(
            action,
            action.targetType(),
            targetId,
            admin.getNickname(),
            detail,
            ipAddress,
            clock.instant()));
  }
}
