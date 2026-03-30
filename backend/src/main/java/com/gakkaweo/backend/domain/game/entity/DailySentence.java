package com.gakkaweo.backend.domain.game.entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "daily_sentences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySentence {

  @Column(unique = true, nullable = false, updatable = false)
  private final UUID publicId = UUID.randomUUID();

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @Setter
  @Column(nullable = false, columnDefinition = "TEXT")
  private String sentence;

  @Setter
  @Column(unique = true)
  private LocalDate usedAt;

  @Setter
  @Column(unique = true)
  private LocalDate scheduledAt;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private DailySentenceStatus status = DailySentenceStatus.ACTIVE;

  @Column(nullable = true)
  private Integer totalPlayers;

  private Instant createdAt;

  public DailySentence(String sentence) {
    this.sentence = sentence;
  }

  public void recordTotalPlayers(int totalPlayers) {
    this.totalPlayers = totalPlayers;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }
}
