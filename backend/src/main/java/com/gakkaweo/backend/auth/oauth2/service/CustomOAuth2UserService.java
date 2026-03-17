package com.gakkaweo.backend.auth.oauth2.service;

import com.gakkaweo.backend.auth.oauth2.CustomOAuth2User;
import com.gakkaweo.backend.auth.oauth2.dto.OAuthAttributes;
import com.gakkaweo.backend.domain.member.entity.Member;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final OAuthMemberService oAuthMemberService;

  public CustomOAuth2UserService(OAuthMemberService oAuthMemberService) {
    this.oAuthMemberService = oAuthMemberService;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);

    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    log.info("OAuth2 사용자 정보 로드: provider={}", registrationId);
    OAuthAttributes attributes = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());
    Member member = oAuthMemberService.findOrCreateMember(attributes);

    return new CustomOAuth2User(member, oAuth2User.getAttributes());
  }
}
