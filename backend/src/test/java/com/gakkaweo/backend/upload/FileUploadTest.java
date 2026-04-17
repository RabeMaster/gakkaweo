package com.gakkaweo.backend.upload;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
