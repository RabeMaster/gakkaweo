package com.gakkaweo.backend.admin.controller;

import com.gakkaweo.backend.admin.dto.CsvUploadResponse;
import com.gakkaweo.backend.admin.dto.DuplicateCheckRequest;
import com.gakkaweo.backend.admin.dto.DuplicateCheckResponse;
import com.gakkaweo.backend.admin.dto.EmergencyReplaceRequest;
import com.gakkaweo.backend.admin.dto.ScheduleRequest;
import com.gakkaweo.backend.admin.dto.SentenceCreateRequest;
import com.gakkaweo.backend.admin.dto.SentenceListResponse;
import com.gakkaweo.backend.admin.dto.SentenceResponse;
import com.gakkaweo.backend.admin.dto.SentenceStatsResponse;
import com.gakkaweo.backend.admin.dto.SentenceUpdateRequest;
import com.gakkaweo.backend.admin.dto.SimilarityTestRequest;
import com.gakkaweo.backend.admin.dto.SimilarityTestResponse;
import com.gakkaweo.backend.admin.dto.UnusedCountResponse;
import com.gakkaweo.backend.admin.service.AdminSentenceService;
import com.gakkaweo.backend.admin.service.CsvUploadService;
import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.config.openapi.AdminErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/sentences")
@RequiredArgsConstructor
@Tag(name = "Admin: Sentences", description = "어드민 문장 관리")
@SecurityRequirement(name = "cookieAuth")
public class AdminSentenceController {

  private final AdminSentenceService adminSentenceService;
  private final CsvUploadService csvUploadService;

  @Operation(
      summary = "문장 목록 조회",
      description =
          """
          정렬 (`sort=field,dir`):
          - 가능 필드: `createdAt`, `status`, `usedAt`, `scheduledAt`, `sentence`
          - 기본값: `createdAt,desc`
          - 잘못된 필드/방향: 400 `VALIDATION_FAILED`""")
  @AdminErrorResponses
  @GetMapping
  public ResponseEntity<SentenceListResponse> getSentences(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(adminSentenceService.getSentences(status, sort, page, size));
  }

  @Operation(
      summary = "문장 등록",
      description =
          """
          에러 코드:
          - `SENTENCE_DUPLICATE` (409): 이미 등록된 문장""",
      responses = @ApiResponse(responseCode = "201", useReturnTypeSchema = true))
  @AdminErrorResponses
  @PostMapping
  public ResponseEntity<SentenceResponse> createSentence(
      @Valid @RequestBody SentenceCreateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    SentenceResponse response =
        adminSentenceService.createSentence(
            request, userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "문장 상세 조회")
  @AdminErrorResponses
  @GetMapping("/{publicId}")
  public ResponseEntity<SentenceResponse> getSentence(@PathVariable UUID publicId) {
    return ResponseEntity.ok(adminSentenceService.getSentence(publicId));
  }

  @Operation(
      summary = "문장 수정",
      description =
          """
          에러 코드:
          - `SENTENCE_NOT_FOUND` (404): 문장 없음
          - `SENTENCE_ALREADY_USED` (400): 이미 출제된 문장
          - `SENTENCE_DUPLICATE` (409): 중복 문장""")
  @AdminErrorResponses
  @PatchMapping("/{publicId}")
  public ResponseEntity<SentenceResponse> updateSentence(
      @PathVariable UUID publicId,
      @Valid @RequestBody SentenceUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    SentenceResponse response =
        adminSentenceService.updateSentence(
            publicId, request, userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "문장 삭제",
      description =
          """
          미출제 문장만 삭제 가능

          에러 코드:
          - `SENTENCE_NOT_FOUND` (404): 문장 없음
          - `SENTENCE_ALREADY_USED` (400): 이미 출제된 문장""")
  @AdminErrorResponses
  @DeleteMapping("/{publicId}")
  public ResponseEntity<Void> deleteSentence(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    adminSentenceService.deleteSentence(
        publicId, userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "문장별 통계 조회")
  @AdminErrorResponses
  @GetMapping("/{publicId}/stats")
  public ResponseEntity<SentenceStatsResponse> getSentenceStats(@PathVariable UUID publicId) {
    return ResponseEntity.ok(adminSentenceService.getSentenceStats(publicId));
  }

  @Operation(summary = "미사용 문장 수 조회")
  @AdminErrorResponses
  @GetMapping("/unused-count")
  public ResponseEntity<UnusedCountResponse> getUnusedCount() {
    return ResponseEntity.ok(new UnusedCountResponse(adminSentenceService.getUnusedCount()));
  }

  @Operation(
      summary = "CSV 업로드",
      description =
          """
          에러 코드:
          - `CSV_PARSE_ERROR` (400): CSV 파싱 실패""",
      responses = @ApiResponse(responseCode = "201", useReturnTypeSchema = true))
  @AdminErrorResponses
  @PostMapping("/upload")
  public ResponseEntity<CsvUploadResponse> uploadCsv(
      @Parameter(description = "CSV 파일 (UTF-8, 줄 단위 문장)") @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    CsvUploadResponse response =
        csvUploadService.uploadCsv(file, userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(
      summary = "스케줄 지정",
      description =
          """
          에러 코드:
          - `SENTENCE_NOT_FOUND` (404): 문장 없음
          - `SENTENCE_ALREADY_USED` (400): 이미 출제된 문장
          - `SENTENCE_ALREADY_SCHEDULED` (409): 해당 날짜에 이미 스케줄 존재""")
  @AdminErrorResponses
  @PostMapping("/{publicId}/schedule")
  public ResponseEntity<SentenceResponse> schedule(
      @PathVariable UUID publicId,
      @Valid @RequestBody ScheduleRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    SentenceResponse response =
        adminSentenceService.schedule(
            publicId, request, userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "스케줄 해제")
  @AdminErrorResponses
  @DeleteMapping("/{publicId}/schedule")
  public ResponseEntity<SentenceResponse> unschedule(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    SentenceResponse response =
        adminSentenceService.unschedule(
            publicId, userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "유사도 테스트",
      description =
          """
          에러 코드:
          - `INVALID_GUESS_TEXT` (400): 유효하지 않은 비교 텍스트
          - `AI_SERVICE_UNAVAILABLE` (503): AI 서비스 불가""")
  @AdminErrorResponses
  @PostMapping("/similarity-test")
  public ResponseEntity<SimilarityTestResponse> testSimilarity(
      @Valid @RequestBody SimilarityTestRequest request) {
    return ResponseEntity.ok(adminSentenceService.testSimilarity(request));
  }

  @Operation(summary = "중복 검사")
  @AdminErrorResponses
  @PostMapping("/duplicate-check")
  public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
      @Valid @RequestBody DuplicateCheckRequest request) {
    return ResponseEntity.ok(adminSentenceService.checkDuplicate(request));
  }

  @Operation(
      summary = "긴급 교체 (SUPERADMIN 전용)",
      description =
          """
          진행 중인 게임에 즉시 영향을 주는 액션이라 SUPERADMIN만 호출 가능.
          기존 세션+추측 삭제, Redis 랭킹 초기화, DAY_CHANGE SSE 브로드캐스트

          에러 코드:
          - `ACCESS_DENIED` (403): SUPERADMIN 권한 없음
          - `SENTENCE_NOT_FOUND` (404): 문장 없음
          - `SENTENCE_ALREADY_USED` (400): 교체 대상이 이미 출제됨""")
  @AdminErrorResponses
  @PostMapping("/emergency-replace")
  public ResponseEntity<SentenceResponse> emergencyReplace(
      @Valid @RequestBody EmergencyReplaceRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    SentenceResponse response =
        adminSentenceService.emergencyReplace(
            request, userDetails.publicId(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }
}
