package com.gakkaweo.backend.infra.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.gakkaweo.backend.infra.notification.client.DiscordWebhookClient;
import com.gakkaweo.backend.infra.notification.config.DiscordWebhookProperties;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@DisplayName("DiscordWebhookClient 단위 테스트 (WireMock)")
class DiscordWebhookClientTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  private DiscordWebhookClient newClient(String webhookUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(1000);
    factory.setReadTimeout(1000);
    RestClient restClient = RestClient.builder().requestFactory(factory).build();
    DiscordWebhookProperties properties =
        new DiscordWebhookProperties(webhookUrl, Duration.ofSeconds(3));
    return new DiscordWebhookClient(restClient, properties);
  }

  @Test
  @DisplayName("sendEmbed - webhookUrl이 빈 문자열이면 요청 보내지 않음")
  void 빈_URL_전송_스킵() {
    DiscordWebhookClient client = newClient("");

    client.sendEmbed(new DiscordEmbed("제목", "설명", 0x57F287, List.of()));

    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook")));
  }

  @Test
  @DisplayName("sendEmbed - webhookUrl이 설정되면 embeds 배열 JSON으로 POST")
  void 정상_전송() {
    wireMock.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(204)));
    DiscordWebhookClient client = newClient(wireMock.baseUrl() + "/webhook");

    DiscordEmbed embed =
        new DiscordEmbed(
            "자정 스케줄러 실행 완료",
            "오늘 문장: ||테스트 문장||",
            0x57F287,
            List.of(new DiscordEmbed.Field("어제 세션 만료", "성공", true)));

    client.sendEmbed(embed);

    wireMock.verify(
        postRequestedFor(urlEqualTo("/webhook"))
            .withRequestBody(matchingJsonPath("$.embeds[0].title", equalTo("자정 스케줄러 실행 완료")))
            .withRequestBody(matchingJsonPath("$.embeds[0].description", containing("||테스트 문장||")))
            .withRequestBody(matchingJsonPath("$.embeds[0].fields[0].name", equalTo("어제 세션 만료"))));
  }

  @Test
  @DisplayName("sendEmbed - Discord가 5xx 응답해도 예외 전파하지 않음")
  void 오류_응답_무시() {
    wireMock.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(500)));
    DiscordWebhookClient client = newClient(wireMock.baseUrl() + "/webhook");

    client.sendEmbed(new DiscordEmbed("t", "d", 0, List.of()));

    wireMock.verify(postRequestedFor(urlEqualTo("/webhook")));
  }
}
