package com.gakkaweo.backend.domain.admin.entity;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

import com.gakkaweo.backend.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sentence_uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentenceUpload {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "admin_id", nullable = false)
  private Member admin;

  @Column(nullable = false, length = 255)
  private String fileName;

  @Column(nullable = false)
  private Integer recordCount;

  private LocalDateTime createdAt;

  public SentenceUpload(Member admin, String fileName, Integer recordCount) {
    this.admin = admin;
    this.fileName = fileName;
    this.recordCount = recordCount;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
  }
}
