package com.gakkaweo.backend.domain.auth.repository;

import com.gakkaweo.backend.domain.member.entity.MemberRole;
import java.time.Instant;
import java.util.UUID;

public interface AuthenticationState {

  UUID getPublicId();

  MemberRole getRole();

  Boolean getBanned();

  boolean isRevoked();

  Instant getExpiresAt();
}
