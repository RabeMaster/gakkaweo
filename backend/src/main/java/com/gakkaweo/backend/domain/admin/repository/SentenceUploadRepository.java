package com.gakkaweo.backend.domain.admin.repository;

import com.gakkaweo.backend.domain.admin.entity.SentenceUpload;
import com.gakkaweo.backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SentenceUploadRepository extends JpaRepository<SentenceUpload, Long> {

  @Modifying
  @Query("UPDATE SentenceUpload s SET s.admin = null WHERE s.admin = :admin")
  int anonymizeByAdmin(@Param("admin") Member admin);
}
