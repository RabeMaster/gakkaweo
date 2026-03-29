package com.gakkaweo.backend.domain.admin.repository;

import com.gakkaweo.backend.domain.admin.entity.AuditLog;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  @Query(
      value =
          "SELECT a FROM AuditLog a JOIN FETCH a.admin"
              + " WHERE (:action IS NULL OR a.action = :action)"
              + " AND (:dateFrom IS NULL OR a.createdAt >= :dateFrom)"
              + " AND (:dateTo IS NULL OR a.createdAt <= :dateTo)"
              + " ORDER BY a.createdAt DESC",
      countQuery =
          "SELECT COUNT(a) FROM AuditLog a"
              + " WHERE (:action IS NULL OR a.action = :action)"
              + " AND (:dateFrom IS NULL OR a.createdAt >= :dateFrom)"
              + " AND (:dateTo IS NULL OR a.createdAt <= :dateTo)")
  Page<AuditLog> findByFilters(
      @Param("action") String action,
      @Param("dateFrom") Instant dateFrom,
      @Param("dateTo") Instant dateTo,
      Pageable pageable);
}
