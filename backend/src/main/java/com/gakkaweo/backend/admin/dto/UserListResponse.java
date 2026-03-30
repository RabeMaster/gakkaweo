package com.gakkaweo.backend.admin.dto;

import java.util.List;

public record UserListResponse(
    List<AdminUserResponse> users, int page, int size, long totalElements, int totalPages) {}
