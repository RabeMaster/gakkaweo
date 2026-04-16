package com.gakkaweo.backend.admin.controller;

import com.gakkaweo.backend.admin.dto.AnnouncementCreateRequest;
import com.gakkaweo.backend.admin.dto.AnnouncementResponse;
import com.gakkaweo.backend.admin.dto.AnnouncementUpdateRequest;
import com.gakkaweo.backend.admin.dto.AuditLogListResponse;
import com.gakkaweo.backend.admin.dto.SystemStatusResponse;
import com.gakkaweo.backend.admin.service.AdminAuditService;
import com.gakkaweo.backend.admin.service.AdminSystemService;
import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.config.openapi.AdminErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system")
@RequiredArgsConstructor
@Transactional
@Tag(name = "Admin: System", description = "어드민 시스템 관리")
@SecurityRequirement(name = "cookieAuth")
public class AdminSystemController {

  private final AdminSystemService adminSystemService;
  private final AdminAuditService adminAuditService;

  @Operation(summary = "공지 목록 조회")
  @AdminErrorResponses
  @GetMapping("/announcements")
  @Transactional(readOnly = true)
  public ResponseEntity<List<AnnouncementResponse>> getAnnouncements() {
    return ResponseEntity.ok(adminSystemService.getAnnouncements());
  }

  @Operation(summary = "공지 등록")
  @AdminErrorResponses
  @PostMapping("/announcements")
  public ResponseEntity<AnnouncementResponse> createAnnouncement(
      @Valid @RequestBody AnnouncementCreateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    AnnouncementResponse response =
        adminSystemService.createAnnouncement(request, userDetails.publicId());
    adminAuditService.log(
        userDetails.publicId(),
        "ANNOUNCEMENT_CREATE",
        "ANNOUNCEMENT",
        response.id().toString(),
        request.title(),
        httpRequest.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "공지 수정")
  @AdminErrorResponses
  @PatchMapping("/announcements/{id}")
  public ResponseEntity<AnnouncementResponse> updateAnnouncement(
      @PathVariable Long id,
      @Valid @RequestBody AnnouncementUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    AnnouncementResponse response = adminSystemService.updateAnnouncement(id, request);
    adminAuditService.log(
        userDetails.publicId(),
        "ANNOUNCEMENT_UPDATE",
        "ANNOUNCEMENT",
        id.toString(),
        null,
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "공지 삭제")
  @AdminErrorResponses
  @DeleteMapping("/announcements/{id}")
  public ResponseEntity<Void> deleteAnnouncement(
      @PathVariable Long id,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    adminSystemService.deleteAnnouncement(id);
    adminAuditService.log(
        userDetails.publicId(),
        "ANNOUNCEMENT_DELETE",
        "ANNOUNCEMENT",
        id.toString(),
        null,
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "시스템 상태 조회")
  @AdminErrorResponses
  @GetMapping("/status")
  @Transactional(readOnly = true)
  public ResponseEntity<SystemStatusResponse> getSystemStatus() {
    return ResponseEntity.ok(adminSystemService.getSystemStatus());
  }

  @Operation(summary = "랭킹 캐시 리셋")
  @AdminErrorResponses
  @PostMapping("/ranking-cache/reset")
  public ResponseEntity<Void> resetRankingCache(
      @AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest httpRequest) {
    adminSystemService.resetRankingCache();
    adminAuditService.log(
        userDetails.publicId(),
        "RANKING_CACHE_RESET",
        "SYSTEM",
        null,
        null,
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Rate Limit 초기화")
  @AdminErrorResponses
  @PostMapping("/rate-limit/reset")
  public ResponseEntity<Void> resetRateLimit(
      @AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest httpRequest) {
    adminSystemService.resetRateLimit();
    adminAuditService.log(
        userDetails.publicId(),
        "RATE_LIMIT_RESET",
        "SYSTEM",
        null,
        null,
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "감사 로그 조회")
  @AdminErrorResponses
  @GetMapping("/audit-logs")
  @Transactional(readOnly = true)
  public ResponseEntity<AuditLogListResponse> getAuditLogs(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant dateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant dateTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(adminSystemService.getAuditLogs(action, dateFrom, dateTo, page, size));
  }
}
