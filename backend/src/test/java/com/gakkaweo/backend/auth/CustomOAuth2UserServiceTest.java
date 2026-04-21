package com.gakkaweo.backend.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gakkaweo.backend.auth.oauth2.CustomOAuth2User;
import com.gakkaweo.backend.auth.oauth2.dto.OAuthAttributes;
import com.gakkaweo.backend.auth.oauth2.service.CustomOAuth2UserService;
import com.gakkaweo.backend.auth.oauth2.service.OAuthMemberService;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

@DisplayName("CustomOAuth2UserService 단위 테스트")
class CustomOAuth2UserServiceTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  private OAuthMemberService oAuthMemberService;
  private CustomOAuth2UserService service;

  @BeforeEach
  void setUp() {
    oAuthMemberService = Mockito.mock(OAuthMemberService.class);
    service = new CustomOAuth2UserService(oAuthMemberService);
  }

  @Test
  @DisplayName("kakao 정상 로드 - CustomOAuth2User 반환")
  void kakao_정상() {
    wireMock.stubFor(
        get(urlEqualTo("/userinfo"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": 98765,
                          "kakao_account": {"email": "user@kakao.com"}
                        }
                        """)));
    Member member = new Member("tester");
    when(oAuthMemberService.findOrCreateMember(any(OAuthAttributes.class))).thenReturn(member);

    OAuth2User result = service.loadUser(userRequest("kakao", "id"));

    assertThat(result).isInstanceOf(CustomOAuth2User.class);
    assertThat(((CustomOAuth2User) result).getMember()).isSameAs(member);
    assertThat(result.getAttributes()).containsKey("kakao_account");
  }

  @Test
  @DisplayName("google 정상 로드 - CustomOAuth2User 반환")
  void google_정상() {
    wireMock.stubFor(
        get(urlEqualTo("/userinfo"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"sub": "g-123", "email": "user@google.com"}
                        """)));
    Member member = new Member("tester");
    when(oAuthMemberService.findOrCreateMember(any(OAuthAttributes.class))).thenReturn(member);

    OAuth2User result = service.loadUser(userRequest("google", "sub"));

    assertThat(result).isInstanceOf(CustomOAuth2User.class);
    assertThat(((CustomOAuth2User) result).getMember()).isSameAs(member);
  }

  @Test
  @DisplayName("banned 회원 - OAuth2AuthenticationException(member_banned)")
  void banned_예외() {
    wireMock.stubFor(
        get(urlEqualTo("/userinfo"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": 11111,
                          "kakao_account": {"email": "banned@kakao.com"}
                        }
                        """)));
    Member banned = new Member("banned");
    banned.setBanned(true);
    banned.setBannedAt(Instant.now());
    when(oAuthMemberService.findOrCreateMember(any(OAuthAttributes.class))).thenReturn(banned);

    assertThatThrownBy(() -> service.loadUser(userRequest("kakao", "id")))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("차단된 계정입니다");
  }

  private OAuth2UserRequest userRequest(String registrationId, String userNameAttribute) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(registrationId)
            .clientId("test-client")
            .clientSecret("test-secret")
            .clientAuthenticationMethod(
                org.springframework.security.oauth2.core.ClientAuthenticationMethod
                    .CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/callback")
            .scope("profile")
            .authorizationUri("http://localhost/authorize")
            .tokenUri("http://localhost/token")
            .userInfoUri(wireMock.baseUrl() + "/userinfo")
            .userNameAttributeName(userNameAttribute)
            .build();

    OAuth2AccessToken token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "fake-token",
            Instant.now(),
            Instant.now().plusSeconds(3600));
    return new OAuth2UserRequest(registration, token);
  }
}
