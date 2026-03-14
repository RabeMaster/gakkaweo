package com.gakkaweo.backend.auth.oauth2.service;

import com.gakkaweo.backend.auth.oauth2.dto.OAuthAttributes;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.entity.SocialAccount;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.repository.SocialAccountRepository;
import com.gakkaweo.backend.domain.member.service.NicknameGenerator;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthMemberService {

  private final SocialAccountRepository socialAccountRepository;
  private final MemberRepository memberRepository;
  private final NicknameGenerator nicknameGenerator;

  public OAuthMemberService(
      SocialAccountRepository socialAccountRepository,
      MemberRepository memberRepository,
      NicknameGenerator nicknameGenerator) {
    this.socialAccountRepository = socialAccountRepository;
    this.memberRepository = memberRepository;
    this.nicknameGenerator = nicknameGenerator;
  }

  @Transactional
  public Member findOrCreateMember(OAuthAttributes attributes) {
    return socialAccountRepository
        .findByProviderAndProviderId(attributes.provider(), attributes.providerId())
        .map(
            existing -> {
              updateEmailIfChanged(existing, attributes.email());
              return existing.getMember();
            })
        .orElseGet(() -> registerNewMember(attributes));
  }

  private void updateEmailIfChanged(SocialAccount socialAccount, String email) {
    if (email != null && !Objects.equals(socialAccount.getEmail(), email)) {
      socialAccount.setEmail(email);
    }
  }

  private Member registerNewMember(OAuthAttributes attributes) {
    String nickname = nicknameGenerator.generate();
    Member member = memberRepository.save(new Member(nickname));

    SocialAccount socialAccount =
        new SocialAccount(member, attributes.provider(), attributes.providerId());
    socialAccount.setEmail(attributes.email());
    socialAccountRepository.save(socialAccount);

    return member;
  }
}
