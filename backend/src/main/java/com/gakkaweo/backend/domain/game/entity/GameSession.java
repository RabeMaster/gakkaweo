package com.gakkaweo.backend.domain.game.entity;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

import com.gakkaweo.backend.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "game_sessions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "sentence_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession {

  @Column(unique = true, nullable = false, updatable = false)
  private final UUID publicId = UUID.randomUUID();

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "sentence_id", nullable = false)
  private DailySentence sentence;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private GameSessionStatus status = GameSessionStatus.IN_PROGRESS;

  @Setter
  @Column(precision = 5, scale = 2)
  private BigDecimal bestSimilarity = BigDecimal.ZERO;

  @Setter private Integer attemptCount = 0;

  @Setter private LocalDateTime clearedAt;

  @Version private Integer version;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  public GameSession(Member member, DailySentence sentence) {
    this.member = member;
    this.sentence = sentence;
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
