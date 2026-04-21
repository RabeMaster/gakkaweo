package com.gakkaweo.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.domain.auth.repository.RefreshTokenRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("회원 탈퇴 통합 테스트")
class AccountDeletionTest extends IntegrationTestBase {

  @Autowired MemberRepository memberRepository;
  @Autowired LocalAccountRepository localAccountRepository;
  @Autowired RefreshTokenRepository refreshTokenRepository;

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
}
