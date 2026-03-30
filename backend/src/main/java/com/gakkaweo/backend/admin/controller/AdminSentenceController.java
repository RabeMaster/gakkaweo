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
import com.gakkaweo.backend.admin.service.AdminAuditService;
import com.gakkaweo.backend.admin.service.AdminSentenceService;
import com.gakkaweo.backend.admin.service.CsvUploadService;
import com.gakkaweo.backend.auth.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/sentences")
@RequiredArgsConstructor
@Transactional
public class AdminSentenceController {

  private final AdminSentenceService adminSentenceService;
  private final CsvUploadService csvUploadService;
  private final AdminAuditService adminAuditService;

  @GetMapping
  @Transactional(readOnly = true)
  public ResponseEntity<SentenceListResponse> getSentences(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(adminSentenceService.getSentences(status, page, size));
  }

  @PostMapping
  public ResponseEntity<SentenceResponse> createSentence(
      @Valid @RequestBody SentenceCreateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    SentenceResponse response = adminSentenceService.createSentence(request);
    adminAuditService.log(
        userDetails.publicId(),
        "SENTENCE_CREATE",
        "SENTENCE",
        response.publicId().toString(),
        request.sentence(),
        httpRequest.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{publicId}")
  @Transactional(readOnly = true)
  public ResponseEntity<SentenceResponse> getSentence(@PathVariable UUID publicId) {
    return ResponseEntity.ok(adminSentenceService.getSentence(publicId));
  }

  @PatchMapping("/{publicId}")
  public ResponseEntity<SentenceResponse> updateSentence(
      @PathVariable UUID publicId,
      @Valid @RequestBody SentenceUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    SentenceResponse response = adminSentenceService.updateSentence(publicId, request);
    adminAuditService.log(
        userDetails.publicId(),
        "SENTENCE_UPDATE",
        "SENTENCE",
        publicId.toString(),
        request.sentence(),
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{publicId}")
  public ResponseEntity<Void> deleteSentence(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    adminSentenceService.deleteSentence(publicId);
    adminAuditService.log(
        userDetails.publicId(),
        "SENTENCE_DELETE",
        "SENTENCE",
        publicId.toString(),
        null,
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{publicId}/stats")
  @Transactional(readOnly = true)
  public ResponseEntity<SentenceStatsResponse> getSentenceStats(@PathVariable UUID publicId) {
    return ResponseEntity.ok(adminSentenceService.getSentenceStats(publicId));
  }

  @GetMapping("/unused-count")
  @Transactional(readOnly = true)
  public ResponseEntity<Map<String, Long>> getUnusedCount() {
    return ResponseEntity.ok(Map.of("count", adminSentenceService.getUnusedCount()));
  }

  @PostMapping("/upload")
  public ResponseEntity<CsvUploadResponse> uploadCsv(
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    CsvUploadResponse response = csvUploadService.uploadCsv(file, userDetails.publicId());
    adminAuditService.log(
        userDetails.publicId(),
        "CSV_UPLOAD",
        "SENTENCE",
        null,
        "success=" + response.successCount() + ", duplicate=" + response.duplicateCount(),
        httpRequest.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/{publicId}/schedule")
  public ResponseEntity<SentenceResponse> schedule(
      @PathVariable UUID publicId,
      @Valid @RequestBody ScheduleRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    SentenceResponse response = adminSentenceService.schedule(publicId, request);
    adminAuditService.log(
        userDetails.publicId(),
        "SENTENCE_SCHEDULE",
        "SENTENCE",
        publicId.toString(),
        "date=" + request.date(),
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{publicId}/schedule")
  public ResponseEntity<SentenceResponse> unschedule(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    SentenceResponse response = adminSentenceService.unschedule(publicId);
    adminAuditService.log(
        userDetails.publicId(),
        "SENTENCE_UNSCHEDULE",
        "SENTENCE",
        publicId.toString(),
        null,
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/similarity-test")
  @Transactional(readOnly = true)
  public ResponseEntity<SimilarityTestResponse> testSimilarity(
      @Valid @RequestBody SimilarityTestRequest request) {
    return ResponseEntity.ok(adminSentenceService.testSimilarity(request));
  }

  @PostMapping("/duplicate-check")
  @Transactional(readOnly = true)
  public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
      @Valid @RequestBody DuplicateCheckRequest request) {
    return ResponseEntity.ok(adminSentenceService.checkDuplicate(request));
  }

  @PostMapping("/emergency-replace")
  public ResponseEntity<SentenceResponse> emergencyReplace(
      @Valid @RequestBody EmergencyReplaceRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    SentenceResponse response = adminSentenceService.emergencyReplace(request);
    adminAuditService.log(
        userDetails.publicId(),
        "EMERGENCY_REPLACE",
        "SENTENCE",
        response.publicId().toString(),
        "newSentence="
            + request.newSentencePublicId()
            + ", returnToPool="
            + request.returnOldToPool(),
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }
}
