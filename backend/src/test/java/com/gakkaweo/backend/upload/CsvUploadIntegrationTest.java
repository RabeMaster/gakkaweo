package com.gakkaweo.backend.upload;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.CsvUploadResponse;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.support.IntegrationTestBase;
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

@DisplayName("CSV 업로드 통합 테스트")
class CsvUploadIntegrationTest extends IntegrationTestBase {

  @Autowired DailySentenceRepository dailySentenceRepository;

  @Test
  @DisplayName("정상 업로드 - 201 + 성공/중복 집계")
  void 정상_업로드() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createActiveSentence("이미 있는 문장");

    String csv = "새 문장 하나\n새 문장 둘\n이미 있는 문장\n";

    ResponseEntity<CsvUploadResponse> response = upload(admin, csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().totalRows()).isEqualTo(3);
    assertThat(response.getBody().successCount()).isEqualTo(2);
    assertThat(response.getBody().duplicateCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("빈 파일 - totalRows=0, success=0")
  void 빈_파일() {
    Member admin = testAuthHelper.createAdmin();
    ResponseEntity<CsvUploadResponse> response = upload(admin, "");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().totalRows()).isEqualTo(0);
    assertThat(response.getBody().successCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("공백 라인 스킵")
  void 공백_라인() {
    Member admin = testAuthHelper.createAdmin();
    String csv = "\n문장A\n   \n문장B\n";

    ResponseEntity<CsvUploadResponse> response = upload(admin, csv);

    assertThat(response.getBody().totalRows()).isEqualTo(2);
    assertThat(response.getBody().successCount()).isEqualTo(2);
  }

  private ResponseEntity<CsvUploadResponse> upload(Member admin, String csvContent) {
    TokenPair tokens = testAuthHelper.issueTokens(admin);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.set(
        HttpHeaders.COOKIE,
        "access_token=" + tokens.accessToken() + "; refresh_token=" + tokens.refreshToken());

    ByteArrayResource resource =
        new ByteArrayResource(csvContent.getBytes()) {
          @Override
          public String getFilename() {
            return "upload.csv";
          }
        };

    HttpHeaders partHeaders = new HttpHeaders();
    partHeaders.setContentType(MediaType.TEXT_PLAIN);
    HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart);

    return restTemplate.exchange(
        url("/admin/sentences/upload"),
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        CsvUploadResponse.class);
  }
}
