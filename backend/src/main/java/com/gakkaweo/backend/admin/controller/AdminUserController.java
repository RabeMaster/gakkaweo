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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class AdminUserController {

  private final AdminUserService adminUserService;
  private final AdminAuditService adminAuditService;

  @GetMapping
  public ResponseEntity<UserListResponse> getUsers(
      @RequestParam(required = false) String nickname,
      @RequestParam(required = false) Boolean banned,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(adminUserService.getUsers(nickname, banned, page, size));
  }

  @GetMapping("/{publicId}")
  public ResponseEntity<UserDetailResponse> getUserDetail(@PathVariable UUID publicId) {
    return ResponseEntity.ok(adminUserService.getUserDetail(publicId));
  }

  @GetMapping("/{publicId}/history")
  public ResponseEntity<UserGameHistoryResponse> getUserHistory(@PathVariable UUID publicId) {
    return ResponseEntity.ok(adminUserService.getUserHistory(publicId));
  }

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
