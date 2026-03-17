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
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private GameSessionStatus status = GameSessionStatus.IN_PROGRESS;

  @Column(precision = 4, scale = 1)
  private BigDecimal bestSimilarity = BigDecimal.ZERO;

  private Integer attemptCount = 0;

  private Instant clearedAt;

  @Version private Integer version;

  private Instant createdAt;

  private Instant updatedAt;

  public GameSession(Member member, DailySentence sentence) {
    this.member = member;
    this.sentence = sentence;
  }

  public void incrementAttempt() {
    this.attemptCount++;
  }

  public void updateBestSimilarity(BigDecimal similarity) {
    if (similarity.compareTo(this.bestSimilarity) > 0) {
      this.bestSimilarity = similarity;
    }
  }

  public void markCleared() {
    this.status = GameSessionStatus.CLEARED;
    this.clearedAt = Instant.now();
  }

  public void markGivenUp() {
    this.status = GameSessionStatus.GIVEN_UP;
  }

  public boolean isInProgress() {
    return this.status == GameSessionStatus.IN_PROGRESS;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
