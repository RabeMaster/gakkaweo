package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.AdminUserResponse;
import com.gakkaweo.backend.admin.dto.ForceNicknameRequest;
import com.gakkaweo.backend.admin.dto.RoleChangeRequest;
import com.gakkaweo.backend.admin.dto.UserDetailResponse;
import com.gakkaweo.backend.admin.dto.UserGameHistoryResponse;
import com.gakkaweo.backend.admin.dto.UserListResponse;
import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
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

@DisplayName("Admin 사용자 관리 통합 테스트")
class AdminUserIntegrationTest extends IntegrationTestBase {

  @Autowired MemberRepository memberRepository;

  @Test
  @DisplayName("역할 변경 - USER → ADMIN 성공")
  void 역할변경_성공() {
    Member admin = testAuthHelper.createAdmin();
    Member target = testAuthHelper.createMember();

    HttpHeaders headers = authedJson(admin);

    ResponseEntity<AdminUserResponse> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId() + "/role"),
            HttpMethod.PATCH,
            new HttpEntity<>(new RoleChangeRequest("ADMIN"), headers),
            AdminUserResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().role()).isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("역할 변경 - 자기 자신 400 ADMIN_SELF_ACTION")
  void 자기자신_400() {
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = authedJson(admin);

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/users/" + admin.getPublicId() + "/role"),
            HttpMethod.PATCH,
            new HttpEntity<>(new RoleChangeRequest("USER"), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("ADMIN_SELF_ACTION");
  }

  @Test
  @DisplayName("역할 변경 - 이미 동일 400 ROLE_ALREADY_ASSIGNED")
  void 동일역할_400() {
    Member admin = testAuthHelper.createAdmin();
    Member target = testAuthHelper.createMember();
    HttpHeaders headers = authedJson(admin);

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId() + "/role"),
            HttpMethod.PATCH,
            new HttpEntity<>(new RoleChangeRequest("USER"), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("ROLE_ALREADY_ASSIGNED");
  }

  @Test
  @DisplayName("차단 - 차단 상태 true + bannedAt 기록")
  void 차단_성공() {
    Member admin = testAuthHelper.createAdmin();
    Member target = testAuthHelper.createMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId() + "/ban"),
            HttpMethod.POST,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Member reloaded = memberRepository.findByPublicId(target.getPublicId()).orElseThrow();
    assertThat(reloaded.getBanned()).isTrue();
    assertThat(reloaded.getBannedAt()).isNotNull();
  }

  @Test
  @DisplayName("차단 해제 - banned=false + bannedAt=null")
  void 차단해제() {
    Member admin = testAuthHelper.createAdmin();
    Member target = testAuthHelper.createBannedMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId() + "/ban"),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Member reloaded = memberRepository.findByPublicId(target.getPublicId()).orElseThrow();
    assertThat(reloaded.getBanned()).isFalse();
    assertThat(reloaded.getBannedAt()).isNull();
  }

  @Test
  @DisplayName("강제 탈퇴 - 사용자 삭제")
  void 강제탈퇴() {
    Member admin = testAuthHelper.createAdmin();
    Member target = testAuthHelper.createMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId()),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(memberRepository.findByPublicId(target.getPublicId())).isEmpty();
  }

  @Test
  @DisplayName("닉네임 강제 변경 - 중복 409 NICKNAME_DUPLICATED")
  void 닉네임_중복() {
    Member admin = testAuthHelper.createAdmin();
    Member existing = testAuthHelper.createMember();
    Member target = testAuthHelper.createMember();

    HttpHeaders headers = authedJson(admin);
    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId() + "/nickname"),
            HttpMethod.PATCH,
            new HttpEntity<>(new ForceNicknameRequest(existing.getNickname()), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("NICKNAME_DUPLICATED");
  }

  @Test
  @DisplayName("프로필 이미지 강제 삭제 - 200 + profileUrl null")
  void 프로필_강제삭제() {
    Member admin = testAuthHelper.createAdmin();
    Member target = testAuthHelper.createMember();
    target.setProfileUrl("/uploads/profile.webp");
    memberRepository.save(target);

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId() + "/profile-image"),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Member reloaded = memberRepository.findByPublicId(target.getPublicId()).orElseThrow();
    assertThat(reloaded.getProfileUrl()).isNull();
  }

  @Test
  @DisplayName("사용자 목록 조회 - 닉네임 필터")
  void 목록_닉네임_필터() {
    Member admin = testAuthHelper.createAdmin();
    testAuthHelper.createMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<UserListResponse> response =
        restTemplate.exchange(
            url("/admin/users?page=0&size=20"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            UserListResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().users()).isNotEmpty();
  }

  @Test
  @DisplayName("사용자 상세 조회 - ActivitySummary 포함")
  void 상세_200() {
    Member admin = testAuthHelper.createAdmin();
    Member target = testAuthHelper.createMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<UserDetailResponse> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId()),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            UserDetailResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().publicId()).isEqualTo(target.getPublicId());
    assertThat(response.getBody().activity()).isNotNull();
  }

  @Test
  @DisplayName("사용자 게임 이력 조회 - 세션 없으면 빈 목록")
  void 게임이력_빈() {
    Member admin = testAuthHelper.createAdmin();
    Member target = testAuthHelper.createMember();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<UserGameHistoryResponse> response =
        restTemplate.exchange(
            url("/admin/users/" + target.getPublicId() + "/history"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            UserGameHistoryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().history()).isEmpty();
  }

  private HttpHeaders authedJson(Member admin) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
