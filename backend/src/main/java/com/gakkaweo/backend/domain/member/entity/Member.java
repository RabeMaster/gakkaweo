package com.gakkaweo.backend.domain.member.entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import com.gakkaweo.backend.domain.common.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseAuditableEntity {

  @Column(unique = true, nullable = false, updatable = false)
  private final UUID publicId = UUID.randomUUID();

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @Setter
  @Column(nullable = false, length = 50)
  private String nickname;

  @Setter
  @Column(length = 500)
  private String profileUrl;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private MemberRole role = MemberRole.USER;

  @Setter
  @Column(nullable = false)
  private Boolean banned = false;

  @Setter private Instant bannedAt;

  public Member(String nickname) {
    this.nickname = nickname;
  }
}
