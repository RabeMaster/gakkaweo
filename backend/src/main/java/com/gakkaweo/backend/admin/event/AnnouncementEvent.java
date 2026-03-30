package com.gakkaweo.backend.admin.event;

import com.gakkaweo.backend.domain.admin.entity.AnnouncementType;

public record AnnouncementEvent(Long id, String title, String content, AnnouncementType type) {}
