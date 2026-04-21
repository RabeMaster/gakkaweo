package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.security.CustomUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CustomUserDetails 단위 테스트")
class CustomUserDetailsTest {

  @Test
  @DisplayName("USER role - ROLE_USER authority")
  void user_권한() {
    CustomUserDetails details = new CustomUserDetails(UUID.randomUUID(), "USER");

    assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
  }

  @Test
  @DisplayName("ADMIN role - ROLE_ADMIN authority")
  void admin_권한() {
    CustomUserDetails details = new CustomUserDetails(UUID.randomUUID(), "ADMIN");

    assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
  }

  @Test
  @DisplayName("getPassword - null 반환")
  void password_null() {
    CustomUserDetails details = new CustomUserDetails(UUID.randomUUID(), "USER");

    assertThat(details.getPassword()).isNull();
  }

  @Test
  @DisplayName("getUsername - publicId 문자열 반환")
  void username_publicId() {
    UUID id = UUID.randomUUID();
    CustomUserDetails details = new CustomUserDetails(id, "USER");

    assertThat(details.getUsername()).isEqualTo(id.toString());
  }
}
