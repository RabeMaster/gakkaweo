package com.gakkaweo.backend.domain.admin.entity;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

import com.gakkaweo.backend.domain.common.entity.BaseTimeEntity;
import com.gakkaweo.backend.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "admin_id")
  private Member admin;

  @Enumerated(STRING)
  @Column(nullable = false, length = 100)
  private AuditAction action;

  @Enumerated(STRING)
  @Column(nullable = false, length = 50)
  private AuditTargetType targetType;

  @Column(length = 255)
  private String targetId;

  @Column(columnDefinition = "TEXT")
  private String detail;

  @Column(length = 45)
  private String ipAddress;

  public AuditLog(
      Member admin,
      AuditAction action,
      AuditTargetType targetType,
      String targetId,
      String detail,
      String ipAddress) {
    this.admin = admin;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.detail = detail;
    this.ipAddress = ipAddress;
  }
}
