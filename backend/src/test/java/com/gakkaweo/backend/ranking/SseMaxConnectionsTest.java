package com.gakkaweo.backend.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.ranking.sse.SseConnectionManager;
import com.gakkaweo.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@DisplayName("SSE 최대 연결 제한 통합 테스트")
@TestPropertySource(properties = {"app.sse.max-connections=2"})
class SseMaxConnectionsTest extends IntegrationTestBase {

  @Autowired SseConnectionManager sseConnectionManager;

  @Test
  @DisplayName("3번째 연결 시도 → SSE_MAX_CONNECTIONS(503)")
  void 최대초과_SSE_MAX() {
    sseConnectionManager.register();
    sseConnectionManager.register();
    assertThat(sseConnectionManager.getConnectionCount()).isEqualTo(2);

    assertThatThrownBy(sseConnectionManager::register)
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.SSE_MAX_CONNECTIONS.getMessage());
  }
}
