package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.oauth2.CustomOAuth2User;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.entity.MemberRole;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CustomOAuth2User 단위 테스트")
class CustomOAuth2UserTest {

  @Test
  @DisplayName("getAuthorities - USER role → ROLE_USER")
  void user_권한() {
    Member member = new Member("tester");
    CustomOAuth2User user = new CustomOAuth2User(member, Map.of("id", "1"));

    assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
  }

  @Test
  @DisplayName("getAuthorities - ADMIN role → ROLE_ADMIN")
  void admin_권한() {
    Member member = new Member("admin");
    member.setRole(MemberRole.ADMIN);
    CustomOAuth2User user = new CustomOAuth2User(member, Map.of("id", "1"));

    assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
  }

  @Test
  @DisplayName("getName - publicId 문자열")
  void getName() {
    Member member = new Member("tester");
    CustomOAuth2User user = new CustomOAuth2User(member, Map.of("id", "1"));

    assertThat(user.getName()).isEqualTo(member.getPublicId().toString());
  }

  @Test
  @DisplayName("getAttributes - 생성자 인자 그대로 반환")
  void attributes() {
    Member member = new Member("tester");
    Map<String, Object> attrs = Map.of("id", "1", "email", "user@example.com");
    CustomOAuth2User user = new CustomOAuth2User(member, attrs);

    assertThat(user.getAttributes()).isEqualTo(attrs);
  }

  @Test
  @DisplayName("getMember - 생성자 인자 Member 반환")
  void getMember() {
    Member member = new Member("tester");
    CustomOAuth2User user = new CustomOAuth2User(member, Map.of());

    assertThat(user.getMember()).isSameAs(member);
  }
}
