package com.gakkaweo.backend.infra.ai.client.dto;

public record SimilarityResponse(double score, String text1, String text2) {}
