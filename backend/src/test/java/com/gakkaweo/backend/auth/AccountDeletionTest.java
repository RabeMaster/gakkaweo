package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.member.config.ProfileImageProperties;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@DisplayName("회원 탈퇴 통합 테스트")
class AccountDeletionTest extends IntegrationTestBase {

  private static final byte[] MINIMAL_WEBP = {
    'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0x20, 0x20, 0x20, 0x20
  };

  @Autowired MemberRepository memberRepository;
  @Autowired LocalAccountRepository localAccountRepository;
  @Autowired RefreshTokenRepository refreshTokenRepository;
  @Autowired ProfileImageProperties profileImageProperties;

  @BeforeEach
  void cleanProfileDir() throws IOException {
    Path dir = Path.of(profileImageProperties.profileDir());
    if (Files.exists(dir)) {
      try (Stream<Path> files = Files.list(dir)) {
        for (Path file : files.toList()) {
          Files.deleteIfExists(file);
        }
      }
    }
  }

  @Test
  @DisplayName("탈퇴 - 200 + 쿠키 삭제 + cascade 삭제")
  void 탈퇴_성공() {
    Member member = testAuthHelper.createMember();
    testAuthHelper.createLocalAccount(member, "deluser", "password123");
    TokenPair tokens = testAuthHelper.issueTokens(member);

    HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.COOKIE,
        "access_token="
            + tokens.accessToken()
            + "; refresh_token="
            + tokens.refreshToken()
            + "; has_session=1");

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/auth/account"), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 쿠키 삭제(MaxAge=0) 3종
    List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(cookies).isNotNull();
    assertThat(cookies.stream().filter(c -> c.contains("Max-Age=0")).count())
        .isGreaterThanOrEqualTo(3);

    UUID publicId = member.getPublicId();
    assertThat(memberRepository.findByPublicId(publicId)).isEmpty();
    assertThat(localAccountRepository.existsByMember(member)).isFalse();
    assertThat(refreshTokenRepository.findByMemberId(member.getId())).isEmpty();
  }

  @Test
  @DisplayName("탈퇴 시 프로필 파일 삭제")
  void 탈퇴_시_프로필_파일_삭제() {
    Member member = testAuthHelper.createMember();
    testAuthHelper.createLocalAccount(member, "profuser", "password123");
    TokenPair tokens = testAuthHelper.issueTokens(member);

    HttpHeaders uploadHeaders = new HttpHeaders();
    uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
    uploadHeaders.set(
        HttpHeaders.COOKIE,
        "access_token=" + tokens.accessToken() + "; refresh_token=" + tokens.refreshToken());

    ByteArrayResource resource =
        new ByteArrayResource(MINIMAL_WEBP) {
          @Override
          public String getFilename() {
            return "profile.webp";
          }
        };

    HttpHeaders partHeaders = new HttpHeaders();
    partHeaders.setContentType(MediaType.valueOf("image/webp"));
    HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart);

    restTemplate.exchange(
        url("/auth/profile-image"),
        HttpMethod.PATCH,
        new HttpEntity<>(body, uploadHeaders),
        AuthResponse.class);

    Path profileFile =
        Path.of(profileImageProperties.profileDir()).resolve(member.getPublicId() + ".webp");
    assertThat(profileFile).exists();

    HttpHeaders deleteHeaders = new HttpHeaders();
    deleteHeaders.set(
        HttpHeaders.COOKIE,
        "access_token="
            + tokens.accessToken()
            + "; refresh_token="
            + tokens.refreshToken()
            + "; has_session=1");

    restTemplate.exchange(
        url("/auth/account"), HttpMethod.DELETE, new HttpEntity<>(deleteHeaders), Void.class);

    assertThat(profileFile).doesNotExist();
  }
}
