package com.gakkaweo.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.ratelimit.filter.EndpointGroup;
import com.gakkaweo.backend.ratelimit.filter.EndpointGroupResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EndpointGroupResolver 단위 테스트")
class EndpointGroupResolverTest {

  private final EndpointGroupResolver resolver = new EndpointGroupResolver();

  @Test
  @DisplayName("NONE - /health")
  void NONE_헬스체크() {
    assertThat(resolver.resolve("GET", "/health")).isEqualTo(EndpointGroup.NONE);
  }

  @Test
  @DisplayName("NONE - /uploads/*, /login/oauth2/*, /oauth2/authorization/*")
  void NONE_업로드_OAuth() {
    assertThat(resolver.resolve("GET", "/uploads/abc.webp")).isEqualTo(EndpointGroup.NONE);
    assertThat(resolver.resolve("GET", "/login/oauth2/code/kakao")).isEqualTo(EndpointGroup.NONE);
    assertThat(resolver.resolve("GET", "/oauth2/authorization/google"))
        .isEqualTo(EndpointGroup.NONE);
  }

  @Test
  @DisplayName("NONE - swagger / api-docs / webjars")
  void NONE_스웨거_문서() {
    assertThat(resolver.resolve("GET", "/swagger-ui/index.html")).isEqualTo(EndpointGroup.NONE);
    assertThat(resolver.resolve("GET", "/v3/api-docs")).isEqualTo(EndpointGroup.NONE);
    assertThat(resolver.resolve("GET", "/swagger-resources/config")).isEqualTo(EndpointGroup.NONE);
    assertThat(resolver.resolve("GET", "/webjars/swagger.js")).isEqualTo(EndpointGroup.NONE);
  }

  @Test
  @DisplayName("ADMIN - /admin/**")
  void admin_경로() {
    assertThat(resolver.resolve("GET", "/admin/users")).isEqualTo(EndpointGroup.ADMIN);
    assertThat(resolver.resolve("POST", "/admin/sentences")).isEqualTo(EndpointGroup.ADMIN);
  }

  @Test
  @DisplayName("READ - GET /auth/me")
  void READ_인증정보_조회() {
    assertThat(resolver.resolve("GET", "/auth/me")).isEqualTo(EndpointGroup.READ);
  }

  @Test
  @DisplayName("AUTH - 다른 /auth 경로")
  void AUTH_다른_auth_경로() {
    assertThat(resolver.resolve("POST", "/auth/login")).isEqualTo(EndpointGroup.AUTH);
    assertThat(resolver.resolve("POST", "/auth/refresh")).isEqualTo(EndpointGroup.AUTH);
  }

  @Test
  @DisplayName("GUESS - POST /daily/guess")
  void GUESS_추측() {
    assertThat(resolver.resolve("POST", "/daily/guess")).isEqualTo(EndpointGroup.GUESS);
  }

  @Test
  @DisplayName("SSE - GET /ranking/stream")
  void SSE_스트림() {
    assertThat(resolver.resolve("GET", "/ranking/stream")).isEqualTo(EndpointGroup.SSE);
  }

  @Test
  @DisplayName("READ - 일반 GET 요청")
  void read_일반GET() {
    assertThat(resolver.resolve("GET", "/daily/today")).isEqualTo(EndpointGroup.READ);
    assertThat(resolver.resolve("GET", "/ranking/list")).isEqualTo(EndpointGroup.READ);
  }

  @Test
  @DisplayName("NONE - 기타 (GUESS 외 POST)")
  void none_기타POST() {
    assertThat(resolver.resolve("POST", "/other/endpoint")).isEqualTo(EndpointGroup.NONE);
    assertThat(resolver.resolve("DELETE", "/daily/something")).isEqualTo(EndpointGroup.NONE);
  }
}
