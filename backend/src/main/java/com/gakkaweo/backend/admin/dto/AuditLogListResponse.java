package com.gakkaweo.backend.admin.dto;

import java.util.List;

public record AuditLogListResponse(
    List<AuditLogResponse> content, int page, int size, long totalElements, int totalPages) {}
