package com.gakkaweo.backend.domain.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

@Getter
@MappedSuperclass
public abstract class BaseAuditableEntity extends BaseTimeEntity {

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  protected Instant updatedAt;
}
