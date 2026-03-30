package com.gakkaweo.backend.controller;

import com.gakkaweo.backend.dto.ActiveAnnouncementResponse;
import com.gakkaweo.backend.service.AnnouncementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

  private final AnnouncementService announcementService;

  @GetMapping("/active")
  public ResponseEntity<List<ActiveAnnouncementResponse>> getActiveAnnouncements() {
    List<ActiveAnnouncementResponse> responses =
        announcementService.getActiveAnnouncements().stream()
            .map(ActiveAnnouncementResponse::from)
            .toList();
    return ResponseEntity.ok(responses);
  }
}
