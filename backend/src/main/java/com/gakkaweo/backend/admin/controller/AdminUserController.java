package com.gakkaweo.backend.admin.controller;

import com.gakkaweo.backend.admin.dto.AdminUserResponse;
import com.gakkaweo.backend.admin.dto.ForceNicknameRequest;
import com.gakkaweo.backend.admin.dto.RoleChangeRequest;
import com.gakkaweo.backend.admin.dto.UserDetailResponse;
import com.gakkaweo.backend.admin.dto.UserGameHistoryResponse;
import com.gakkaweo.backend.admin.dto.UserListResponse;
import com.gakkaweo.backend.admin.service.AdminAuditService;
import com.gakkaweo.backend.admin.service.AdminUserService;
import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.config.openapi.AdminErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Slf4j
@Transactional
@Tag(name = "Admin: Users", description = "어드민 사용자 관리")
@SecurityRequirement(name = "cookieAuth")
public class AdminUserController {

  private final AdminUserService adminUserService;
  private final AdminAuditService adminAuditService;

  @Operation(summary = "사용자 목록 조회")
  @AdminErrorResponses
  @GetMapping
  @Transactional(readOnly = true)
  public ResponseEntity<UserListResponse> getUsers(
      @RequestParam(required = false) String nickname,
      @RequestParam(required = false) Boolean banned,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(adminUserService.getUsers(nickname, banned, page, size));
  }

  @Operation(summary = "사용자 상세 조회")
  @AdminErrorResponses
  @GetMapping("/{publicId}")
  @Transactional(readOnly = true)
  public ResponseEntity<UserDetailResponse> getUserDetail(@PathVariable UUID publicId) {
    return ResponseEntity.ok(adminUserService.getUserDetail(publicId));
  }

  @Operation(summary = "사용자 게임 이력 조회")
  @AdminErrorResponses
  @GetMapping("/{publicId}/history")
  @Transactional(readOnly = true)
  public ResponseEntity<UserGameHistoryResponse> getUserHistory(@PathVariable UUID publicId) {
    return ResponseEntity.ok(adminUserService.getUserHistory(publicId));
  }

  @Operation(
      summary = "역할 변경",
      description =
          """
          에러 코드:
          - `MEMBER_NOT_FOUND` (404): 사용자 없음
          - `ADMIN_SELF_ACTION` (400): 자기 자신의 역할 변경 불가
          - `ROLE_ALREADY_ASSIGNED` (400): 이미 동일한 역할""")
  @AdminErrorResponses
  @PatchMapping("/{publicId}/role")
  public ResponseEntity<AdminUserResponse> changeRole(
      @PathVariable UUID publicId,
      @Valid @RequestBody RoleChangeRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    AdminUserResponse response =
        adminUserService.changeRole(publicId, userDetails.publicId(), request);
    adminAuditService.log(
        userDetails.publicId(),
        "ROLE_CHANGE",
        "MEMBER",
        publicId.toString(),
        "newRole=" + request.role(),
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "사용자 차단",
      description =
          """
          에러 코드:
          - `MEMBER_NOT_FOUND` (404): 사용자 없음
          - `ADMIN_SELF_ACTION` (400): 자기 자신 차단 불가""")
  @AdminErrorResponses
  @PostMapping("/{publicId}/ban")
  public ResponseEntity<Void> banUser(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    adminUserService.banUser(publicId, userDetails.publicId());
    adminAuditService.log(
        userDetails.publicId(),
        "USER_BAN",
        "MEMBER",
        publicId.toString(),
        null,
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "차단 해제",
      description =
          """
          에러 코드:
          - `MEMBER_NOT_FOUND` (404): 사용자 없음
          - `ADMIN_SELF_ACTION` (400): 자기 자신 해제 불가""")
  @AdminErrorResponses
  @DeleteMapping("/{publicId}/ban")
  public ResponseEntity<Void> unbanUser(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    adminUserService.unbanUser(publicId, userDetails.publicId());
    adminAuditService.log(
        userDetails.publicId(),
        "USER_UNBAN",
        "MEMBER",
        publicId.toString(),
        null,
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "강제 탈퇴",
      description =
          """
          에러 코드:
          - `MEMBER_NOT_FOUND` (404): 사용자 없음
          - `ADMIN_SELF_ACTION` (400): 자기 자신 탈퇴 불가""")
  @AdminErrorResponses
  @DeleteMapping("/{publicId}")
  public ResponseEntity<Void> forceDeleteUser(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    adminUserService.forceDeleteUser(publicId, userDetails.publicId());
    adminAuditService.log(
        userDetails.publicId(),
        "USER_FORCE_DELETE",
        "MEMBER",
        publicId.toString(),
        null,
        httpRequest.getRemoteAddr());
    try {
      adminUserService.cleanupRedisAfterDelete(publicId);
      adminUserService.cleanupProfileImage(publicId);
    } catch (Exception e) {
      log.warn("강제 탈퇴 후 정리 실패: publicId={}", publicId, e);
    }
    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "닉네임 강제 변경",
      description =
          """
          에러 코드:
          - `MEMBER_NOT_FOUND` (404): 사용자 없음
          - `ADMIN_SELF_ACTION` (400): 자기 자신의 닉네임 변경 불가
          - `NICKNAME_DUPLICATED` (409): 이미 사용 중인 닉네임
          - `NICKNAME_FORBIDDEN` (400): 사용할 수 없는 닉네임""")
  @AdminErrorResponses
  @PatchMapping("/{publicId}/nickname")
  public ResponseEntity<AdminUserResponse> forceChangeNickname(
      @PathVariable UUID publicId,
      @Valid @RequestBody ForceNicknameRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    AdminUserResponse response =
        adminUserService.forceChangeNickname(publicId, userDetails.publicId(), request);
    adminUserService.syncNicknameToRedis(publicId, response.nickname());
    adminAuditService.log(
        userDetails.publicId(),
        "USER_FORCE_NICKNAME",
        "MEMBER",
        publicId.toString(),
        "newNickname=" + response.nickname(),
        httpRequest.getRemoteAddr());
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "프로필 이미지 강제 삭제",
      description =
          """
          에러 코드:
          - `MEMBER_NOT_FOUND` (404): 사용자 없음
          - `ADMIN_SELF_ACTION` (400): 자기 자신의 이미지 삭제 불가""")
  @AdminErrorResponses
  @DeleteMapping("/{publicId}/profile-image")
  public ResponseEntity<Void> forceDeleteProfileImage(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletRequest httpRequest) {
    adminUserService.forceDeleteProfileImage(publicId, userDetails.publicId());
    adminAuditService.log(
        userDetails.publicId(),
        "USER_FORCE_PROFILE_DELETE",
        "MEMBER",
        publicId.toString(),
        null,
        httpRequest.getRemoteAddr());
    try {
      adminUserService.cleanupProfileImageAndRedis(publicId);
    } catch (Exception e) {
      log.warn("프로필 이미지 정리 실패: publicId={}", publicId, e);
    }
    return ResponseEntity.ok().build();
  }
}
