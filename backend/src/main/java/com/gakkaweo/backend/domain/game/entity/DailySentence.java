package com.gakkaweo.backend.domain.game.entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false, updatable = false)
  private UUID publicId = UUID.randomUUID();

  @Column(nullable = false, columnDefinition = "TEXT")
  private String sentence;

  @Setter
  @Column(unique = true)
  private LocalDate usedAt;

  @Setter private byte[] embedding;

  @Setter
  @Column(length = 100)
  private String modelVersion;

  @Setter
  @Column(length = 20)
  private String status = "ACTIVE";

  private LocalDateTime createdAt;

  public DailySentence(String sentence) {
    this.sentence = sentence;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
  }
}
