package com.gakkaweo.backend.domain.game.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guess_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuessHistory {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private GameSession session;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String guessText;

  @Column(nullable = false, precision = 4, scale = 1)
  private BigDecimal similarity;

  @Column(nullable = false)
  private Integer attemptNumber;

  private Instant createdAt;

  public GuessHistory(
      GameSession session, String guessText, BigDecimal similarity, Integer attemptNumber) {
    this.session = session;
    this.guessText = guessText;
    this.similarity = similarity;
    this.attemptNumber = attemptNumber;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }
}
