package com.gakkaweo.backend.domain.member.repository;

import com.gakkaweo.backend.domain.member.entity.Member;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByPublicId(UUID publicId);

  boolean existsByNickname(String nickname);

  @Query(
      value =
          "SELECT m FROM Member m WHERE"
              + " (:nickname IS NULL OR LOWER(m.nickname) LIKE LOWER(CONCAT('%', :nickname, '%')))"
              + " AND (:banned IS NULL OR m.banned = :banned)"
              + " ORDER BY m.createdAt DESC",
      countQuery =
          "SELECT COUNT(m) FROM Member m WHERE"
              + " (:nickname IS NULL OR LOWER(m.nickname) LIKE LOWER(CONCAT('%', :nickname, '%')))"
              + " AND (:banned IS NULL OR m.banned = :banned)")
  Page<Member> findByFilters(
      @Param("nickname") String nickname, @Param("banned") Boolean banned, Pageable pageable);

  @Query("SELECT COUNT(m) FROM Member m WHERE m.createdAt >= :from AND m.createdAt < :to")
  long countByCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);
}
