package com.gakkaweo.backend.admin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DisplayName("어드민 감사 로그 Discord 알림 통합 테스트")
class AuditLogNotificationIntegrationTest extends IntegrationTestBase {

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
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("app.notification.discord.webhook-url", () -> WIRE_MOCK.baseUrl() + "/webhook");
    registry.add("app.notification.discord.mention-role-id", () -> "role-123");
  }

  @BeforeEach
  void stubDiscord() {
    WIRE_MOCK.resetAll();
    WIRE_MOCK.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(204)));
  }

  @Test
  @DisplayName("관리자 BAN 호출 → Discord WireMock으로 HIGH 레벨 알림 수신")
  void BAN_호출_Discord_수신() {
    Member admin = testAuthHelper.createAdmin();
    Member target = testAuthHelper.createMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId() + "/ban"),
            HttpMethod.POST,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(
            () ->
                WIRE_MOCK.verify(
                    postRequestedFor(urlEqualTo("/webhook"))
                        .withRequestBody(matchingJsonPath("$.content", equalTo("<@&role-123>")))
                        .withRequestBody(
                            matchingJsonPath("$.allowed_mentions.roles[0]", equalTo("role-123")))
                        .withRequestBody(
                            matchingJsonPath("$.embeds[0].title", equalTo("어드민 감사 액션")))
                        .withRequestBody(
                            matchingJsonPath("$.embeds[0].fields[0].value", equalTo("USER_BAN")))
                        .withRequestBody(
                            matchingJsonPath(
                                "$.embeds[0].fields[?(@.name=='Target')].value",
                                containing("MEMBER")))));
  }
}
