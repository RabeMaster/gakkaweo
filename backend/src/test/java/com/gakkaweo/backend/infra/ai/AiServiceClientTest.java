package com.gakkaweo.backend.infra.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.infra.ai.client.AiServiceClient;
import com.gakkaweo.backend.infra.ai.client.dto.SimilarityResponse;
import com.gakkaweo.backend.infra.ai.exception.AiServiceException;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@DisplayName("AiServiceClient 단위 테스트 (WireMock)")
class AiServiceClientTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  private AiServiceClient client;

  @BeforeEach
  void setUp() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2000);
    factory.setReadTimeout(2000);
    RestClient restClient =
        RestClient.builder().baseUrl(wireMock.baseUrl()).requestFactory(factory).build();
    client = new AiServiceClient(restClient);
  }

  @Test
  @DisplayName("calculateSimilarity - 정상 응답 파싱")
  void 정상_응답() {
    wireMock.stubFor(
        post(urlEqualTo("/similarity"))
            .withRequestBody(equalToJson("{\"sentence\":\"원문\",\"guess\":\"추측\"}"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"score\": 73.5}")));

    SimilarityResponse response = client.calculateSimilarity("원문", "추측");

    assertThat(response.score()).isEqualTo(73.5);
  }

  @Test
  @DisplayName("calculateSimilarity - 5xx 응답 시 AiServiceException")
  void 서버_오류_예외() {
    wireMock.stubFor(post(urlEqualTo("/similarity")).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client.calculateSimilarity("원문", "추측"))
        .isInstanceOf(AiServiceException.class)
        .hasMessageContaining("AI 서비스를 일시적으로 이용할 수 없습니다");
  }

  @Test
  @DisplayName("calculateSimilarity - 4xx 응답 시 AiServiceException")
  void 클라이언트_오류_예외() {
    wireMock.stubFor(
        post(urlEqualTo("/similarity"))
            .willReturn(aResponse().withStatus(400).withBody("bad request")));

    assertThatThrownBy(() -> client.calculateSimilarity("원문", "추측"))
        .isInstanceOf(AiServiceException.class);
  }

  @Test
  @DisplayName("isHealthy - 200 응답 시 true")
  void isHealthy_true() {
    wireMock.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));

    assertThat(client.isHealthy()).isTrue();
  }

  @Test
  @DisplayName("isHealthy - 5xx 응답 시 false")
  void isHealthy_서버오류() {
    wireMock.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(503)));

    assertThat(client.isHealthy()).isFalse();
  }

  @Test
  @DisplayName("isHealthy - 연결 실패 시 false")
  void isHealthy_연결실패() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(300);
    factory.setReadTimeout(300);
    RestClient deadClient =
        RestClient.builder().baseUrl("http://127.0.0.1:1").requestFactory(factory).build();
    AiServiceClient unreachable = new AiServiceClient(deadClient);

    assertThat(unreachable.isHealthy()).isFalse();
  }
}
