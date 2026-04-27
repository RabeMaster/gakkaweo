package com.gakkaweo.backend.auth.oauth2.service;

import com.gakkaweo.backend.auth.oauth2.CustomOAuth2User;
import com.gakkaweo.backend.auth.oauth2.dto.OAuthAttributes;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.member.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private static final String OAUTH2_BANNED_ERROR_CODE = "member_banned";

  private final OAuthMemberService oAuthMemberService;

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);

    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    log.info("OAuth2 사용자 정보 로드: provider={}", registrationId);
    OAuthAttributes attributes = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());
    Member member = oAuthMemberService.findOrCreateMember(attributes);

    if (Boolean.TRUE.equals(member.getBanned())) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAUTH2_BANNED_ERROR_CODE, ErrorCode.MEMBER_BANNED.getMessage(), null));
    }

    return new CustomOAuth2User(member, oAuth2User.getAttributes());
  }
}
