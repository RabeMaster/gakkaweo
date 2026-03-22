package com.gakkaweo.backend.domain.member.repository;

import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.entity.SocialAccount;
import com.gakkaweo.backend.domain.member.entity.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

  @EntityGraph(attributePaths = "member")
  Optional<SocialAccount> findByProviderAndProviderId(SocialProvider provider, String providerId);

  @Modifying
  @Query("DELETE FROM SocialAccount s WHERE s.member = :member")
  int deleteByMember(@Param("member") Member member);
}
