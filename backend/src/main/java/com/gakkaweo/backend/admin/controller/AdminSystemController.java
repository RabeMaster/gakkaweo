package com.gakkaweo.backend.admin.controller;

import com.gakkaweo.backend.admin.dto.AnnouncementCreateRequest;
import com.gakkaweo.backend.admin.dto.AnnouncementResponse;
import com.gakkaweo.backend.admin.dto.AnnouncementUpdateRequest;
import com.gakkaweo.backend.admin.dto.AuditLogListResponse;
import com.gakkaweo.backend.admin.dto.SystemStatusResponse;
import com.gakkaweo.backend.admin.service.AdminSystemService;
import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.config.openapi.AdminErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = "Admin: System", description = "어드민 시스템 관리")
@SecurityRequirement(name = "cookieAuth")
public class AdminSystemController {

  private final AdminSystemService adminSystemService;

  @Operation(summary = "공지 목록 조회")
  @AdminErrorResponses
  @GetMapping("/announcements")
  public ResponseEntity<List<AnnouncementResponse>> getAnnouncements() {
    return ResponseEntity.ok(adminSystemService.getAnnouncements());
  }

  @Operation(
      summary = "공지 등록",
      responses = @ApiResponse(responseCode = "201", useReturnTypeSchema = true))
  @AdminErrorResponses
  @PostMapping("/announcements")
  public ResponseEntity<AnnouncementResponse> createAnnouncement(
      @Valid @RequestBody AnnouncementCreateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    AnnouncementResponse response =
        adminSystemService.createAnnouncement(
            request, userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(
      summary = "공지 수정",
      description =
          """
          에러 코드:
          - `ANNOUNCEMENT_NOT_FOUND` (404): 공지 없음""")
  @AdminErrorResponses
  @PatchMapping("/announcements/{id}")
  public ResponseEntity<AnnouncementResponse> updateAnnouncement(
      @PathVariable Long id,
      @Valid @RequestBody AnnouncementUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    AnnouncementResponse response =
        adminSystemService.updateAnnouncement(
            id, request, userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "공지 삭제",
      description =
          """
          에러 코드:
          - `ANNOUNCEMENT_NOT_FOUND` (404): 공지 없음""")
  @AdminErrorResponses
  @DeleteMapping("/announcements/{id}")
  public ResponseEntity<Void> deleteAnnouncement(
      @PathVariable Long id,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    adminSystemService.deleteAnnouncement(id, userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "시스템 상태 조회")
  @AdminErrorResponses
  @GetMapping("/status")
  public ResponseEntity<SystemStatusResponse> getSystemStatus() {
    return ResponseEntity.ok(adminSystemService.getSystemStatus());
  }

  @Operation(
      summary = "랭킹 캐시 리셋 (SUPERADMIN 전용)",
      description = "Redis Sorted Set 전체 초기화. 운영 사고 가능성으로 SUPERADMIN만 호출 가능.")
  @AdminErrorResponses
  @PostMapping("/ranking-cache/reset")
  public ResponseEntity<Void> resetRankingCache(
      @AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest httpRequest) {
    adminSystemService.resetRankingCache(userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "Rate Limit 초기화 (SUPERADMIN 전용)",
      description = "어뷰저 IP 차단을 해제하는 효과로 보안 영향이 있어 SUPERADMIN만 호출 가능.")
  @AdminErrorResponses
  @PostMapping("/rate-limit/reset")
  public ResponseEntity<Void> resetRateLimit(
      @AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest httpRequest) {
    adminSystemService.resetRateLimit(userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "감사 로그 조회",
      description =
          """
          정렬 (`sort=field,dir`):
          - 가능 필드: `createdAt`, `action`
          - 기본값: `createdAt,desc`
          - 잘못된 필드/방향: 400 `VALIDATION_FAILED`""")
  @AdminErrorResponses
  @GetMapping("/audit-logs")
  public ResponseEntity<AuditLogListResponse> getAuditLogs(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant dateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant dateTo,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        adminSystemService.getAuditLogs(action, dateFrom, dateTo, sort, page, size));
  }
}
