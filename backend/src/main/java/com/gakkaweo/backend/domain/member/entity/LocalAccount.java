package com.gakkaweo.backend.domain.member.entity;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "local_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalAccount {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "member_id", nullable = false, unique = true)
  private Member member;

  @Column(nullable = false, length = 20, unique = true)
  private String username;

  @Column(nullable = false, length = 72)
  private String passwordHash;

  private Instant createdAt;

  public LocalAccount(Member member, String username, String passwordHash) {
    this.member = member;
    this.username = username;
    this.passwordHash = passwordHash;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }
}
