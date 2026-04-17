package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.auth.dto.NicknameUpdateRequest;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.service.NicknameGenerator;
import com.gakkaweo.backend.domain.member.validation.NicknameValidator;
import com.gakkaweo.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@DisplayName("닉네임 검증 통합 테스트")
class NicknameValidationTest extends IntegrationTestBase {

  @Autowired NicknameValidator nicknameValidator;
  @Autowired NicknameGenerator nicknameGenerator;

  @Test
  @DisplayName("정상 닉네임 변경 - 200")
  void 정상변경_200() {
    Member member = testAuthHelper.createMember();
    ResponseEntity<AuthResponse> response = patchNickname(member, "새닉네임");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().nickname()).isEqualTo("새닉네임");
  }

  @Test
  @DisplayName("현재와 동일 - 400 NICKNAME_UNCHANGED")
  void 동일닉네임_400() {
    Member member = testAuthHelper.createMember();
    ResponseEntity<ErrorBody> response = patchNicknameExpectError(member, member.getNickname());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("NICKNAME_UNCHANGED");
  }

  @Test
  @DisplayName("중복 닉네임 - 409 NICKNAME_DUPLICATED")
  void 중복_409() {
    testAuthHelper.createMember(); // 기존 회원
    Member existing = testAuthHelper.createMember();
    Member me = testAuthHelper.createMember();

    ResponseEntity<ErrorBody> response = patchNicknameExpectError(me, existing.getNickname());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("NICKNAME_DUPLICATED");
  }

  @Test
  @DisplayName("금칙어 - 400 NICKNAME_FORBIDDEN")
  void 금칙어_400() {
    Member member = testAuthHelper.createMember();
    ResponseEntity<ErrorBody> response = patchNicknameExpectError(member, "관리자");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("NICKNAME_FORBIDDEN");
  }

  @Test
  @DisplayName("NicknameValidator - 금칙어/길이 검증")
  void validator_단위검증() {
    nicknameValidator.validate("정상닉네임");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> nicknameValidator.validate("a"))
        .hasMessageContaining("요청 검증");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> nicknameValidator.validate("admin"))
        .hasMessageContaining("사용할 수 없는 닉네임");
  }

  @Test
  @DisplayName("NicknameGenerator - 생성된 닉네임이 validator 통과")
  void generator_유효성() {
    for (int i = 0; i < 10; i++) {
      String nickname = nicknameGenerator.generate();
      nicknameValidator.validate(nickname);
      assertThat(nickname).isNotBlank();
    }
  }

  private ResponseEntity<AuthResponse> patchNickname(Member member, String nickname) {
    return exchange(member, nickname, AuthResponse.class);
  }

  private ResponseEntity<ErrorBody> patchNicknameExpectError(Member member, String nickname) {
    return exchange(member, nickname, ErrorBody.class);
  }

  private <T> ResponseEntity<T> exchange(Member member, String nickname, Class<T> type) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<NicknameUpdateRequest> request =
        new HttpEntity<>(new NicknameUpdateRequest(nickname), headers);
    return restTemplate.exchange(url("/auth/nickname"), HttpMethod.PATCH, request, type);
  }
}
