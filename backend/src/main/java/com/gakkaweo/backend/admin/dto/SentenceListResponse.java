package com.gakkaweo.backend.admin.dto;

import java.util.List;

public record SentenceListResponse(
    List<SentenceResponse> sentences, int page, int size, long totalElements, int totalPages) {}
