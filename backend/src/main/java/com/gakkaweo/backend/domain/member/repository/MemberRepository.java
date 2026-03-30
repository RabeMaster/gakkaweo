package com.gakkaweo.backend.domain.member.repository;

import com.gakkaweo.backend.domain.member.entity.Member;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository
    extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {

  Optional<Member> findByPublicId(UUID publicId);

  boolean existsByNickname(String nickname);

  @Query("SELECT COUNT(m) FROM Member m WHERE m.createdAt >= :from AND m.createdAt < :to")
  long countByCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);
}
