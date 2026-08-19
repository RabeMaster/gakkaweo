package com.gakkaweo.backend.announcement;

import com.gakkaweo.backend.domain.admin.entity.Announcement;
import com.gakkaweo.backend.domain.admin.repository.AnnouncementRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

  private final AnnouncementRepository announcementRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<Announcement> getActiveAnnouncements() {
    return announcementRepository.findActiveAnnouncements(clock.instant());
  }
}
