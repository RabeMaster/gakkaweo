package com.gakkaweo.backend.domain.member.repository;

import com.gakkaweo.backend.domain.member.entity.Member;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByPublicId(UUID publicId);
}
