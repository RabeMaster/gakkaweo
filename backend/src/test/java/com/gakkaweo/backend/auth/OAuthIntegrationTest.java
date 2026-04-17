package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.auth.oauth2.dto.OAuthAttributes;
import com.gakkaweo.backend.auth.oauth2.service.OAuthMemberService;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.entity.SocialProvider;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.repository.SocialAccountRepository;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("OAuth 통합 테스트")
class OAuthIntegrationTest extends IntegrationTestBase {

  @Autowired OAuthMemberService oAuthMemberService;
  @Autowired MemberRepository memberRepository;
  @Autowired SocialAccountRepository socialAccountRepository;

  @Test
  @DisplayName("신규 소셜 계정 - Member + SocialAccount 생성")
  void 신규_생성() {
    OAuthAttributes attributes =
        new OAuthAttributes(SocialProvider.KAKAO, "kakao-123", "test@kakao.com");

    Member member = oAuthMemberService.findOrCreateMember(attributes);

    assertThat(member.getPublicId()).isNotNull();
    assertThat(member.getNickname()).isNotBlank();
    assertThat(
            socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao-123"))
        .isPresent();
  }

  @Test
  @DisplayName("기존 소셜 계정 - 동일 Member 반환 (중복 생성 방지)")
  void 기존_동일Member_반환() {
    OAuthAttributes attributes =
        new OAuthAttributes(SocialProvider.GOOGLE, "google-456", "test@google.com");

    Member first = oAuthMemberService.findOrCreateMember(attributes);
    Member second = oAuthMemberService.findOrCreateMember(attributes);

    assertThat(second.getPublicId()).isEqualTo(first.getPublicId());
    assertThat(memberRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("다른 provider로 로그인 - 신규 SocialAccount 생성 (별개 Member)")
  void 다른provider_별개Member() {
    oAuthMemberService.findOrCreateMember(
        new OAuthAttributes(SocialProvider.KAKAO, "kakao-1", "a@kakao.com"));
    oAuthMemberService.findOrCreateMember(
        new OAuthAttributes(SocialProvider.GOOGLE, "google-2", "b@google.com"));

    assertThat(memberRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("이메일 변경 - 기존 SocialAccount 이메일 업데이트")
  void 이메일_업데이트() {
    OAuthAttributes initial = new OAuthAttributes(SocialProvider.NAVER, "naver-1", "old@naver.com");
    oAuthMemberService.findOrCreateMember(initial);

    OAuthAttributes updated = new OAuthAttributes(SocialProvider.NAVER, "naver-1", "new@naver.com");
    oAuthMemberService.findOrCreateMember(updated);

    String email =
        socialAccountRepository
            .findByProviderAndProviderId(SocialProvider.NAVER, "naver-1")
            .orElseThrow()
            .getEmail();
    assertThat(email).isEqualTo("new@naver.com");
  }

  @Test
  @DisplayName("OAuthAttributes - 지원하지 않는 provider 예외")
  void 미지원_provider() {
    assertThatThrownBy(() -> OAuthAttributes.of("unsupported", Map.of()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED.getMessage());
  }

  @Test
  @DisplayName("OAuthAttributes - 카카오 변환")
  void 카카오_attributes() {
    OAuthAttributes result =
        OAuthAttributes.of(
            "kakao", Map.of("id", 12345L, "kakao_account", Map.of("email", "user@kakao.com")));

    assertThat(result.provider()).isEqualTo(SocialProvider.KAKAO);
    assertThat(result.providerId()).isEqualTo("12345");
    assertThat(result.email()).isEqualTo("user@kakao.com");
  }

  @Test
  @DisplayName("OAuthAttributes - 구글 변환")
  void 구글_attributes() {
    OAuthAttributes result =
        OAuthAttributes.of("google", Map.of("sub", "abc-123", "email", "user@google.com"));

    assertThat(result.provider()).isEqualTo(SocialProvider.GOOGLE);
    assertThat(result.providerId()).isEqualTo("abc-123");
    assertThat(result.email()).isEqualTo("user@google.com");
  }

  @Test
  @DisplayName("OAuthAttributes - 네이버 response 누락 예외")
  void 네이버_response_없음() {
    assertThatThrownBy(() -> OAuthAttributes.of("naver", Map.of()))
        .isInstanceOf(BusinessException.class);
  }
}
