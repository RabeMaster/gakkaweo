package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.auth.dto.RegisterRequest;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@DisplayName("쿠키 속성 통합 테스트")
class CookieAttributeTest extends IntegrationTestBase {

  @Test
  @DisplayName("회원가입 시 access/refresh/has_session 쿠키 속성 검증")
  void 쿠키속성_검증() {
    ResponseEntity<AuthResponse> response =
        restTemplate.postForEntity(
            url("/auth/register"),
            new RegisterRequest("cookieuser", "password123"),
            AuthResponse.class);

    List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(cookies).isNotNull();

    Map<String, String> byName = indexByName(cookies);

    String access = byName.get("access_token");
    assertThat(access).isNotNull();
    assertThat(access).containsIgnoringCase("HttpOnly");
    assertThat(access).contains("Path=/");
    assertThat(access).containsIgnoringCase("SameSite=Lax");

    String refresh = byName.get("refresh_token");
    assertThat(refresh).isNotNull();
    assertThat(refresh).containsIgnoringCase("HttpOnly");
    assertThat(refresh).contains("Path=/auth/refresh");
    assertThat(refresh).containsIgnoringCase("SameSite=Lax");

    String session = byName.get("has_session");
    assertThat(session).isNotNull();
    assertThat(session).doesNotContainIgnoringCase("HttpOnly");
    assertThat(session).contains("Path=/");
  }

  private Map<String, String> indexByName(List<String> setCookieHeaders) {
    return setCookieHeaders.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                h -> h.substring(0, h.indexOf('=')),
                h -> h,
                (a, b) -> a,
                java.util.LinkedHashMap::new));
  }
}
