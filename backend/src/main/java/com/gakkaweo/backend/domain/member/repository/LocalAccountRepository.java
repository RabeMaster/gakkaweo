package com.gakkaweo.backend.domain.member.repository;

import com.gakkaweo.backend.domain.member.entity.LocalAccount;
import com.gakkaweo.backend.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocalAccountRepository extends JpaRepository<LocalAccount, Long> {

  @EntityGraph(attributePaths = "member")
  Optional<LocalAccount> findByUsernameIgnoreCase(String username);

  boolean existsByUsernameIgnoreCase(String username);

  boolean existsByMember(Member member);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM LocalAccount l WHERE l.member = :member")
  int deleteByMember(@Param("member") Member member);
}
