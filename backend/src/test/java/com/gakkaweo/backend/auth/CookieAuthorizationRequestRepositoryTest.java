package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gakkaweo.backend.auth.config.CookieProperties;
import com.gakkaweo.backend.auth.oauth2.CookieAuthorizationRequestRepository;
import jakarta.servlet.http.Cookie;
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@DisplayName("CookieAuthorizationRequestRepository 단위 테스트")
class CookieAuthorizationRequestRepositoryTest {

  private static final String COOKIE_NAME = "oauth2_auth_request";

  private CookieAuthorizationRequestRepository repository;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    repository =
        new CookieAuthorizationRequestRepository(
            new CookieProperties(true, "example.com"), objectMapper);
  }

  @Test
  @DisplayName("쿠키 없음 - load null 반환")
  void 쿠키_없음_null() {
    OAuth2AuthorizationRequest loaded =
        repository.loadAuthorizationRequest(new MockHttpServletRequest());

    assertThat(loaded).isNull();
  }

  @Test
  @DisplayName("다른 쿠키만 존재 - load null 반환")
  void 다른_쿠키만_null() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("unrelated", "value"));

    OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);

    assertThat(loaded).isNull();
  }

  @Test
  @DisplayName("save 후 load - 라운드트립 복원")
  void 라운드트립() {
    OAuth2AuthorizationRequest original = sampleRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), response);

    HttpCookie parsed = firstSetCookie(response);
    assertThat(parsed.getName()).isEqualTo(COOKIE_NAME);
    assertThat(parsed.getMaxAge()).isEqualTo(300);
    assertThat(parsed.getSecure()).isTrue();
    assertThat(parsed.isHttpOnly()).isTrue();

    MockHttpServletRequest loadRequest = new MockHttpServletRequest();
    loadRequest.setCookies(new Cookie(parsed.getName(), parsed.getValue()));
    OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

    assertThat(loaded).isNotNull();
    assertThat(loaded.getAuthorizationUri()).isEqualTo(original.getAuthorizationUri());
    assertThat(loaded.getClientId()).isEqualTo(original.getClientId());
    assertThat(loaded.getRedirectUri()).isEqualTo(original.getRedirectUri());
    assertThat(loaded.getState()).isEqualTo(original.getState());
    assertThat(loaded.getScopes()).containsExactlyInAnyOrderElementsOf(original.getScopes());
  }

  @Test
  @DisplayName("save null 인자 - 쿠키 삭제 헤더 발급")
  void save_null_삭제() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    repository.saveAuthorizationRequest(null, new MockHttpServletRequest(), response);

    HttpCookie parsed = firstSetCookie(response);
    assertThat(parsed.getMaxAge()).isZero();
    assertThat(parsed.getValue()).isEmpty();
  }

  @Test
  @DisplayName("remove - 저장된 값 반환 후 삭제 쿠키 발급")
  void remove_반환_후_삭제() {
    OAuth2AuthorizationRequest original = sampleRequest();
    MockHttpServletResponse saveResponse = new MockHttpServletResponse();
    repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);
    HttpCookie saved = firstSetCookie(saveResponse);

    MockHttpServletRequest removeRequest = new MockHttpServletRequest();
    removeRequest.setCookies(new Cookie(saved.getName(), saved.getValue()));
    MockHttpServletResponse removeResponse = new MockHttpServletResponse();

    OAuth2AuthorizationRequest removed =
        repository.removeAuthorizationRequest(removeRequest, removeResponse);

    assertThat(removed).isNotNull();
    assertThat(removed.getState()).isEqualTo(original.getState());

    HttpCookie deleted = firstSetCookie(removeResponse);
    assertThat(deleted.getMaxAge()).isZero();
  }

  @Test
  @DisplayName("remove - 쿠키 없을 때 null 반환 + 삭제 쿠키 발급 안 함")
  void remove_쿠키없음() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    OAuth2AuthorizationRequest removed =
        repository.removeAuthorizationRequest(new MockHttpServletRequest(), response);

    assertThat(removed).isNull();
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  @DisplayName("deleteCookie - 단독 호출 시 만료 헤더 발급")
  void deleteCookie_단독() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    repository.deleteCookie(response);

    HttpCookie parsed = firstSetCookie(response);
    assertThat(parsed.getMaxAge()).isZero();
  }

  @Test
  @DisplayName("잘못된 쿠키 값 - IllegalStateException")
  void 역직렬화_실패() {
    // Base64 URL-safe 디코딩은 성공하지만 JSON 역직렬화 단계에서 IOException 유발
    String invalidJsonBase64 =
        Base64.getUrlEncoder().encodeToString("not-json-content".getBytes(StandardCharsets.UTF_8));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(COOKIE_NAME, invalidJsonBase64));

    assertThatThrownBy(() -> repository.loadAuthorizationRequest(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("역직렬화 실패");
  }

  @Test
  @DisplayName("secure=false 설정 - 쿠키 secure 플래그 미설정")
  void secure_false() {
    CookieAuthorizationRequestRepository insecureRepository =
        new CookieAuthorizationRequestRepository(
            new CookieProperties(false, "example.com"), objectMapper);
    MockHttpServletResponse response = new MockHttpServletResponse();

    insecureRepository.saveAuthorizationRequest(
        sampleRequest(), new MockHttpServletRequest(), response);

    HttpCookie parsed = firstSetCookie(response);
    assertThat(parsed.getSecure()).isFalse();
  }

  private OAuth2AuthorizationRequest sampleRequest() {
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri("https://provider.example/authorize")
        .clientId("client-1")
        .redirectUri("https://gakkaweo.example/callback")
        .scopes(Set.of("profile", "email"))
        .state("state-xyz")
        .additionalParameters(Map.of("nonce", "n-1"))
        .authorizationRequestUri("https://provider.example/authorize?state=state-xyz")
        .attributes(attrs -> attrs.put("registration_id", "kakao"))
        .build();
  }

  private HttpCookie firstSetCookie(MockHttpServletResponse response) {
    String header = response.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(header).as("Set-Cookie header").isNotNull();
    List<HttpCookie> parsed = HttpCookie.parse(header);
    assertThat(parsed).isNotEmpty();
    return parsed.stream().filter(c -> COOKIE_NAME.equals(c.getName())).findFirst().orElseThrow();
  }
}
