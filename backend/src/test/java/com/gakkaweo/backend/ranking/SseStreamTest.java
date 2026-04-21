package com.gakkaweo.backend.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.ranking.sse.SseConnectionManager;
import com.gakkaweo.backend.ranking.sse.SseEventType;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("SSE 스트림 end-to-end 통합 테스트")
class SseStreamTest extends IntegrationTestBase {

  @Autowired SseConnectionManager sseConnectionManager;

  @Test
  @DisplayName("구독 후 broadcast 이벤트 실제 수신")
  void 구독_후_broadcast_수신() throws Exception {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url("/ranking/stream")))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

    CompletableFuture<HttpResponse<Stream<String>>> responseFuture =
        client.sendAsync(request, HttpResponse.BodyHandlers.ofLines());

    HttpResponse<Stream<String>> response = responseFuture.get(5, TimeUnit.SECONDS);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("text/event-stream");

    List<String> received = new CopyOnWriteArrayList<>();
    Thread reader =
        Executors.defaultThreadFactory()
            .newThread(
                () -> {
                  try {
                    response.body().limit(40).forEach(received::add);
                  } catch (Exception ignored) {
                  }
                });
    reader.setDaemon(true);
    reader.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(sseConnectionManager.getConnectionCount()).isEqualTo(1));

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              String joined = String.join("\n", received);
              assertThat(joined).contains(SseEventType.HEARTBEAT.name());
              assertThat(joined).contains(SseEventType.RANKING_UPDATE.name());
            });

    sseConnectionManager.broadcast(SseEventType.RANKING_UPDATE, "payload-marker");

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              String joined = String.join("\n", received);
              assertThat(joined).contains("payload-marker");
            });

    reader.interrupt();
    client.close();
  }
}
