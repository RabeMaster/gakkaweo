package com.gakkaweo.backend.support;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

public class NoOpResponseErrorHandler implements ResponseErrorHandler {

  @Override
  public boolean hasError(ClientHttpResponse response) {
    return false;
  }

  @Override
  public void handleError(ClientHttpResponse response) {
    // 테스트에서 상태 코드를 직접 검증하므로 예외를 던지지 않는다.
  }
}
