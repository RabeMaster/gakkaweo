package com.gakkaweo.backend.domain.member.entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false, updatable = false)
  private UUID publicId = UUID.randomUUID();

  @Setter
  @Column(nullable = false, length = 50)
  private String nickname;

  @Setter
  @Column(length = 500)
  private String profileUrl;

  @Setter
  @Column(length = 20)
  private String role = "USER";

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  public Member(String nickname) {
    this.nickname = nickname;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
