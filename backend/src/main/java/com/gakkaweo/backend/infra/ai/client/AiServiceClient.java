package com.gakkaweo.backend.infra.ai.client;

import com.gakkaweo.backend.common.exception.ErrorCode;
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

  public SimilarityResponse calculateSimilarity(String sentence, String guess) {
    try {
      SimilarityResponse response =
          aiServiceRestClient
              .post()
              .uri("/similarity")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new SimilarityRequest(sentence, guess))
              .retrieve()
              .body(SimilarityResponse.class);
      if (response == null
          || response.score() == null
          || !Double.isFinite(response.score())
          || response.score() < 0
          || response.score() > 100) {
        throw new AiServiceException("AI 응답 점수가 유효하지 않습니다", null);
      }
      return response;
    } catch (RestClientException e) {
      throw new AiServiceException(ErrorCode.AI_SERVICE_UNAVAILABLE.getMessage(), e);
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
