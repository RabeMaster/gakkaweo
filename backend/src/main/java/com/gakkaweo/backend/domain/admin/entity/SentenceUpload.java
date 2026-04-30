package com.gakkaweo.backend.domain.admin.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sentence_uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentenceUpload extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "admin_id", nullable = true)
  private Member admin;

  @Column(nullable = false, length = 255)
  private String fileName;

  @Column(nullable = false)
  private Integer recordCount;

  public SentenceUpload(Member admin, String fileName, Integer recordCount) {
    this.admin = admin;
    this.fileName = fileName;
    this.recordCount = recordCount;
  }
}
