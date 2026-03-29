package com.gakkaweo.backend.admin.dto;

public record CsvUploadResponse(int totalRows, int successCount, int duplicateCount) {}
