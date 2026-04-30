package com.gakkaweo.backend.domain.admin.entity;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

import com.gakkaweo.backend.domain.common.entity.BaseTimeEntity;
import com.gakkaweo.backend.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "admin_id")
  private Member admin;

  @Setter
  @Column(nullable = false, length = 200)
  private String title;

  @Setter
  @Column(columnDefinition = "TEXT")
  private String content;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AnnouncementType type = AnnouncementType.INFO;

  @Setter
  @Column(nullable = false)
  private Boolean active = true;

  @Setter
  @Column(nullable = false)
  private Instant startsAt;

  @Setter private Instant endsAt;

  public Announcement(
      Member admin,
      String title,
      String content,
      AnnouncementType type,
      Instant startsAt,
      Instant endsAt) {
    this.admin = admin;
    this.title = title;
    this.content = content;
    this.type = type;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
  }
}
