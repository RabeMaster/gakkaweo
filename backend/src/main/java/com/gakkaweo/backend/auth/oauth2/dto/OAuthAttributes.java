package com.gakkaweo.backend.auth.oauth2.dto;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.member.entity.SocialProvider;
import java.util.Map;

public record OAuthAttributes(SocialProvider provider, String providerId, String email) {

  public static OAuthAttributes of(String registrationId, Map<String, Object> attributes) {
    return switch (registrationId) {
      case "kakao" -> ofKakao(attributes);
      case "google" -> ofGoogle(attributes);
      case "naver" -> ofNaver(attributes);
      default -> throw new BusinessException(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED);
    };
  }

  @SuppressWarnings("unchecked")
  private static OAuthAttributes ofKakao(Map<String, Object> attributes) {
    String providerId = String.valueOf(attributes.get("id"));
    Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
    String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
    return new OAuthAttributes(SocialProvider.KAKAO, providerId, email);
  }

  private static OAuthAttributes ofGoogle(Map<String, Object> attributes) {
    String providerId = (String) attributes.get("sub");
    String email = (String) attributes.get("email");
    return new OAuthAttributes(SocialProvider.GOOGLE, providerId, email);
  }

  @SuppressWarnings("unchecked")
  private static OAuthAttributes ofNaver(Map<String, Object> attributes) {
    Map<String, Object> response = (Map<String, Object>) attributes.get("response");
    if (response == null) {
      throw new BusinessException(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED);
    }
    String providerId = (String) response.get("id");
    String email = (String) response.get("email");
    return new OAuthAttributes(SocialProvider.NAVER, providerId, email);
  }
}
