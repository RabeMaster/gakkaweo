package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "문장 목록 응답")
public record SentenceListResponse(
    @Schema(description = "문장 목록") List<SentenceResponse> content,
    @Schema(description = "현재 페이지", example = "0") int page,
    @Schema(description = "페이지 크기", example = "20") int size,
    @Schema(description = "전체 건수", example = "200") long totalElements,
    @Schema(description = "전체 페이지 수", example = "10") int totalPages) {

  public static SentenceListResponse from(Page<DailySentence> page) {
    return new SentenceListResponse(
        page.getContent().stream().map(SentenceResponse::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
