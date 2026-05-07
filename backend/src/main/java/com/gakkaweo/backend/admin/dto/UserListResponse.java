package com.gakkaweo.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "사용자 목록 응답")
public record UserListResponse(
    @Schema(description = "사용자 목록") List<AdminUserResponse> content,
    @Schema(description = "현재 페이지", example = "0") int page,
    @Schema(description = "페이지 크기", example = "20") int size,
    @Schema(description = "전체 건수", example = "150") long totalElements,
    @Schema(description = "전체 페이지 수", example = "8") int totalPages) {

  public static UserListResponse from(List<AdminUserResponse> users, Page<?> page) {
    return new UserListResponse(
        users, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }
}
