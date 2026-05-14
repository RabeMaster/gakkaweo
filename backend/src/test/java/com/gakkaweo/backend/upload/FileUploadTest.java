package com.gakkaweo.backend.upload;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.config.ProfileImageProperties;
import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

@DisplayName("파일 업로드 통합 테스트")
class FileUploadTest extends IntegrationTestBase {

  private static final byte[] MINIMAL_WEBP = {
    'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0x20, 0x20, 0x20, 0x20
  };

  private static final byte[] MINIMAL_PNG = {
    (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0
  };

  private static final byte[] SHORT_FILE = {'R', 'I', 'F', 'F'};

  private static final byte[] MINIMAL_JPEG = {
    (byte) 0xFF,
    (byte) 0xD8,
    (byte) 0xFF,
    (byte) 0xE0,
    0x00,
    0x10,
    0x4A,
    0x46,
    0x49,
    0x46,
    0x00,
    0x01
  };

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
  @DisplayName("WebP 업로드 성공")
  void webp_성공() {
    Member member = testAuthHelper.createMember();
    HttpEntity<MultiValueMap<String, Object>> request =
        multipartRequest(member, MINIMAL_WEBP, "image/webp", "profile.webp");

    ResponseEntity<AuthResponse> response =
        restTemplate.exchange(
            url("/auth/profile-image"), HttpMethod.PATCH, request, AuthResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().profileUrl()).contains(member.getPublicId().toString());
  }

  @Test
  @DisplayName("WebP 업로드 성공 - 파일 시스템 검증")
  void webp_성공_파일시스템_검증() {
    Member member = testAuthHelper.createMember();
    HttpEntity<MultiValueMap<String, Object>> request =
        multipartRequest(member, MINIMAL_WEBP, "image/webp", "profile.webp");

    restTemplate.exchange(
        url("/auth/profile-image"), HttpMethod.PATCH, request, AuthResponse.class);

    Path saved = profilePath(member);
    assertThat(saved).exists();
    assertThat(saved).hasSize(MINIMAL_WEBP.length);
  }

  @Test
  @DisplayName("PNG 거부 - INVALID_FILE_TYPE(400)")
  void png_거부() {
    Member member = testAuthHelper.createMember();
    HttpEntity<MultiValueMap<String, Object>> request =
        multipartRequest(member, MINIMAL_PNG, "image/png", "profile.png");

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/profile-image"), HttpMethod.PATCH, request, ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_FILE_TYPE");
  }

  @Test
  @DisplayName("text/plain → INVALID_FILE_TYPE")
  void 텍스트_거부() {
    Member member = testAuthHelper.createMember();
    HttpEntity<MultiValueMap<String, Object>> request =
        multipartRequest(member, "not an image".getBytes(), "text/plain", "notes.txt");

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/profile-image"), HttpMethod.PATCH, request, ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_FILE_TYPE");
  }

  @Test
  @DisplayName("12바이트 미만 파일 거부 - INVALID_FILE_TYPE")
  void 짧은_파일_거부() {
    Member member = testAuthHelper.createMember();
    HttpEntity<MultiValueMap<String, Object>> request =
        multipartRequest(member, SHORT_FILE, "image/webp", "profile.webp");

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/profile-image"), HttpMethod.PATCH, request, ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_FILE_TYPE");
  }

  @Test
  @DisplayName("JPEG magic bytes 거부 - MIME 통과하나 시그니처 불일치")
  void jpeg_바이트_거부() {
    Member member = testAuthHelper.createMember();
    HttpEntity<MultiValueMap<String, Object>> request =
        multipartRequest(member, MINIMAL_JPEG, "image/jpeg", "profile.jpg");

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/auth/profile-image"), HttpMethod.PATCH, request, ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_FILE_TYPE");
  }

  @Test
  @DisplayName("업로드 미인증 - 401")
  void 업로드_미인증_401() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    ByteArrayResource resource =
        new ByteArrayResource(MINIMAL_WEBP) {
          @Override
          public String getFilename() {
            return "profile.webp";
          }
        };

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", new HttpEntity<>(resource));

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/auth/profile-image"),
            HttpMethod.PATCH,
            new HttpEntity<>(body, headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("프로필 삭제 성공 - profileUrl null + 파일 삭제")
  void 프로필_삭제_성공() {
    Member member = testAuthHelper.createMember();
    HttpEntity<MultiValueMap<String, Object>> uploadRequest =
        multipartRequest(member, MINIMAL_WEBP, "image/webp", "profile.webp");
    restTemplate.exchange(
        url("/auth/profile-image"), HttpMethod.PATCH, uploadRequest, AuthResponse.class);

    assertThat(profilePath(member)).exists();

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);
    ResponseEntity<AuthResponse> response =
        restTemplate.exchange(
            url("/auth/profile-image"),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            AuthResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().profileUrl()).isNull();
    assertThat(profilePath(member)).doesNotExist();
  }

  @Test
  @DisplayName("프로필 삭제 미인증 - 401")
  void 프로필_삭제_미인증_401() {
    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/auth/profile-image"), HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("재업로드 시 기존 파일 덮어쓰기 - 파일 1개만 존재")
  void 업로드_후_재업로드_덮어쓰기() throws IOException {
    Member member = testAuthHelper.createMember();

    HttpEntity<MultiValueMap<String, Object>> first =
        multipartRequest(member, MINIMAL_WEBP, "image/webp", "profile.webp");
    restTemplate.exchange(url("/auth/profile-image"), HttpMethod.PATCH, first, AuthResponse.class);

    byte[] secondWebp = {
      'R', 'I', 'F', 'F', 1, 0, 0, 0, 'W', 'E', 'B', 'P', 0x21, 0x21, 0x21, 0x21, 0x00
    };
    HttpEntity<MultiValueMap<String, Object>> second =
        multipartRequest(member, secondWebp, "image/webp", "profile.webp");
    restTemplate.exchange(url("/auth/profile-image"), HttpMethod.PATCH, second, AuthResponse.class);

    Path saved = profilePath(member);
    assertThat(saved).exists();
    assertThat(saved).hasSize(secondWebp.length);

    try (Stream<Path> files = Files.list(Path.of(profileImageProperties.profileDir()))) {
      long count =
          files
              .filter(p -> p.getFileName().toString().contains(member.getPublicId().toString()))
              .count();
      assertThat(count).isEqualTo(1);
    }
  }

  private Path profilePath(Member member) {
    return Path.of(profileImageProperties.profileDir()).resolve(member.getPublicId() + ".webp");
  }

  private HttpEntity<MultiValueMap<String, Object>> multipartRequest(
      Member member, byte[] content, String contentType, String filename) {
    TokenPair tokens = testAuthHelper.issueTokens(member);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.set(
        HttpHeaders.COOKIE,
        "access_token=" + tokens.accessToken() + "; refresh_token=" + tokens.refreshToken());

    ByteArrayResource resource =
        new ByteArrayResource(content) {
          @Override
          public String getFilename() {
            return filename;
          }
        };

    HttpHeaders partHeaders = new HttpHeaders();
    partHeaders.setContentType(MediaType.valueOf(contentType));
    HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart);

    return new HttpEntity<>(body, headers);
  }
}
