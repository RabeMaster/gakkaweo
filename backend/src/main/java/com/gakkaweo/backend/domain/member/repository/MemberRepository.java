package com.gakkaweo.backend.domain.member.repository;

import com.gakkaweo.backend.domain.member.entity.Member;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository
    extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {

  Optional<Member> findByPublicId(UUID publicId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM Member m WHERE m.publicId = :publicId")
  Optional<Member> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM Member m WHERE m.id = :id")
  Optional<Member> findByIdForUpdate(@Param("id") Long id);

  boolean existsByNickname(String nickname);

  @Query("SELECT COUNT(m) FROM Member m WHERE m.createdAt >= :from AND m.createdAt < :to")
  long countByCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);
}
