package com.gakkaweo.backend.game;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.awaitility.Awaitility.await;

import com.gakkaweo.backend.game.scheduler.DailySentenceScheduler;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DisplayName("DailySentenceScheduler -> Discord WireMock end-to-end 통합 테스트")
class DailySentenceSchedulerDiscordIntegrationTest extends IntegrationTestBase {

  private static final WireMockServer WIRE_MOCK =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  static {
    WIRE_MOCK.start();
  }

  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK.stop();
  }

  @DynamicPropertySource
  static void overrideWebhookUrl(DynamicPropertyRegistry registry) {
    registry.add("app.notification.discord.webhook-url", () -> WIRE_MOCK.baseUrl() + "/webhook");
  }

  @Autowired DailySentenceScheduler scheduler;

  @BeforeEach
  void stubDiscord() {
    WIRE_MOCK.resetAll();
    WIRE_MOCK.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(204)));
  }

  @Test
  @DisplayName("executeMidnightJob 실행 시 Discord로 POST 발송 + embed 본문에 스포일러 문장 포함")
  void 자정_스케줄러_실제_Discord_전송() {
    testAuthHelper.createActiveSentence("통합 테스트 문장");

    scheduler.executeMidnightJob();

    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(
            () ->
                WIRE_MOCK.verify(
                    postRequestedFor(urlEqualTo("/webhook"))
                        .withRequestBody(matchingJsonPath("$.embeds[0].title"))
                        .withRequestBody(
                            matchingJsonPath(
                                "$.embeds[0].description", containing("||통합 테스트 문장||")))
                        .withRequestBody(matchingJsonPath("$.embeds[0].fields[0].name"))));
  }
}
