package com.gakkaweo.backend.domain.auth.entity;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

import com.gakkaweo.backend.domain.common.entity.BaseTimeEntity;
import com.gakkaweo.backend.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession extends BaseTimeEntity {

  @Column(nullable = false, unique = true, updatable = false)
  private final UUID publicId = UUID.randomUUID();

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(nullable = false)
  private boolean revoked;

  @Column(nullable = false)
  private Instant expiresAt;

  public AuthSession(Member member, Instant expiresAt) {
    this.member = member;
    this.expiresAt = expiresAt;
  }

  public void revoke() {
    revoked = true;
  }

  public void extend(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}
