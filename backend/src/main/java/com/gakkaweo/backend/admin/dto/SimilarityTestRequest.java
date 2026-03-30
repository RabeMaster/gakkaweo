package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record SimilarityTestRequest(@NotBlank String sentence, @NotBlank String guessText) {}
