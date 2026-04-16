package com.gakkaweo.backend.controller;

import com.gakkaweo.backend.config.openapi.StandardErrorResponses;
import com.gakkaweo.backend.dto.ActiveAnnouncementResponse;
import com.gakkaweo.backend.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/announcements")
@RequiredArgsConstructor
@Tag(name = "Announcement", description = "공지사항")
public class AnnouncementController {

  private final AnnouncementService announcementService;

  @Operation(summary = "활성 공지 조회")
  @StandardErrorResponses
  @GetMapping("/active")
  public ResponseEntity<List<ActiveAnnouncementResponse>> getActiveAnnouncements() {
    List<ActiveAnnouncementResponse> responses =
        announcementService.getActiveAnnouncements().stream()
            .map(ActiveAnnouncementResponse::from)
            .toList();
    return ResponseEntity.ok(responses);
  }
}
