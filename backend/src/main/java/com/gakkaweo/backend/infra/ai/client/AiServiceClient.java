package com.gakkaweo.backend.infra.ai.client;

import com.gakkaweo.backend.infra.ai.client.dto.SimilarityRequest;
import com.gakkaweo.backend.infra.ai.client.dto.SimilarityResponse;
import com.gakkaweo.backend.infra.ai.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class AiServiceClient {

  private final RestClient aiServiceRestClient;

  public SimilarityResponse calculateSimilarity(String text1, String text2) {
    try {
      return aiServiceRestClient
          .post()
          .uri("/similarity")
          .contentType(MediaType.APPLICATION_JSON)
          .body(new SimilarityRequest(text1, text2))
          .retrieve()
          .body(SimilarityResponse.class);
    } catch (RestClientException e) {
      throw new AiServiceException("AI 서비스 요청 실패", e);
    }
  }

  public boolean isHealthy() {
    try {
      aiServiceRestClient.get().uri("/health").retrieve().toBodilessEntity();
      return true;
    } catch (RestClientException e) {
      return false;
    }
  }
}
