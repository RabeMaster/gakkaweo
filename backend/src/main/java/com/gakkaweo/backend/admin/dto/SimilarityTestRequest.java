package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SimilarityTestRequest(
    @NotBlank @Size(max = 500) String sentence, @NotBlank @Size(max = 200) String guessText) {}
