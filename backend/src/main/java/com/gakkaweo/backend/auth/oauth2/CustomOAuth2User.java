package com.gakkaweo.backend.auth.oauth2;

import com.gakkaweo.backend.domain.member.entity.Member;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Getter
public class CustomOAuth2User implements OAuth2User {

  private final Member member;
  private final Map<String, Object> attributes;

  public CustomOAuth2User(Member member, Map<String, Object> attributes) {
    this.member = member;
    this.attributes = attributes;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.singleton(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
  }

  @Override
  public String getName() {
    return member.getPublicId().toString();
  }
}
