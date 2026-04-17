package com.gakkaweo.backend.support;

import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestContainerConfig.class, TestClockConfig.class})
public abstract class IntegrationTestBase {

  @LocalServerPort protected int port;

  @Autowired protected DatabaseCleaner databaseCleaner;

  @Autowired protected TestSimilarityClient testSimilarityClient;

  @Autowired(required = false)
  protected TestAuthHelper testAuthHelper;

  @Autowired(required = false)
  protected TestEventCollector testEventCollector;

  @Autowired protected Clock clock;

  protected final RestTemplate restTemplate =
      new RestTemplateBuilder().errorHandler(new NoOpResponseErrorHandler()).build();

  @BeforeEach
  void cleanBeforeEach() {
    databaseCleaner.clean();
    testSimilarityClient.reset();
    if (clock instanceof TestClock testClock) {
      testClock.setInstant(
          java.time.Clock.system(com.gakkaweo.backend.common.time.TimeConstants.KST).instant());
    }
    if (testEventCollector != null) {
      testEventCollector.reset();
    }
  }

  protected String baseUrl() {
    return "http://localhost:" + port;
  }

  protected String url(String path) {
    return baseUrl() + path;
  }
}
