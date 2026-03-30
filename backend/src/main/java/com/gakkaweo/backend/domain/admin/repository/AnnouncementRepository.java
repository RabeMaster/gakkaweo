package com.gakkaweo.backend.domain.admin.repository;

import com.gakkaweo.backend.domain.admin.entity.Announcement;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

  @Query(
      "SELECT a FROM Announcement a"
          + " WHERE a.active = true"
          + " AND a.startsAt <= :now"
          + " AND (a.endsAt IS NULL OR a.endsAt >= :now)"
          + " ORDER BY a.createdAt DESC")
  List<Announcement> findActiveAnnouncements(@Param("now") Instant now);

  @Query("SELECT a FROM Announcement a LEFT JOIN FETCH a.admin ORDER BY a.createdAt DESC")
  List<Announcement> findAllWithAdmin();
}
