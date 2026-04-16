package com.gakkaweo.backend.auth.controller;

import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.auth.dto.LoginRequest;
import com.gakkaweo.backend.auth.dto.NicknameUpdateRequest;
import com.gakkaweo.backend.auth.dto.RegisterRequest;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.auth.service.AccountService;
import com.gakkaweo.backend.auth.service.AuthService;
import com.gakkaweo.backend.auth.service.LocalAuthService;
import com.gakkaweo.backend.auth.service.ProfileImageService;
import com.gakkaweo.backend.auth.util.CookieUtils;
import com.gakkaweo.backend.config.openapi.StandardErrorResponses;
import com.gakkaweo.backend.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 (회원가입, 로그인, 토큰, 프로필)")
public class AuthController {

  private final AuthService authService;
  private final LocalAuthService localAuthService;
  private final AccountService accountService;
  private final ProfileImageService profileImageService;
  private final CookieUtils cookieUtils;

  @Operation(
      summary = "회원가입",
      responses =
          @ApiResponse(
              responseCode = "201",
              headers = @Header(name = "Set-Cookie", schema = @Schema(type = "string"))))
  @StandardErrorResponses
  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    Member member = localAuthService.register(request.username(), request.password());
    return issueTokensAndRespond(member, HttpStatus.CREATED);
  }

  @Operation(
      summary = "로그인",
      responses =
          @ApiResponse(
              responseCode = "200",
              headers = @Header(name = "Set-Cookie", schema = @Schema(type = "string"))))
  @StandardErrorResponses
  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    Member member = localAuthService.authenticate(request.username(), request.password());
    return issueTokensAndRespond(member, HttpStatus.OK);
  }

  @Operation(
      summary = "토큰 갱신",
      responses =
          @ApiResponse(
              responseCode = "200",
              headers = @Header(name = "Set-Cookie", schema = @Schema(type = "string"))))
  @StandardErrorResponses
  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(@CookieValue("refresh_token") String refreshToken) {
    TokenPair tokenPair = authService.refresh(refreshToken);

    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            cookieUtils.createAccessTokenCookie(tokenPair.accessToken()).toString())
        .header(
            HttpHeaders.SET_COOKIE,
            cookieUtils.createRefreshTokenCookie(tokenPair.refreshToken()).toString())
        .header(HttpHeaders.SET_COOKIE, cookieUtils.createSessionIndicatorCookie().toString())
        .build();
  }

  @Operation(summary = "로그아웃")
  @SecurityRequirement(name = "cookieAuth")
  @StandardErrorResponses
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(value = "access_token", required = false) String accessToken) {
    if (accessToken != null) {
      authService.logout(accessToken);
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteAccessTokenCookie().toString())
        .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteRefreshTokenCookie().toString())
        .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteSessionIndicatorCookie().toString())
        .build();
  }

  @Operation(summary = "내 정보 조회")
  @SecurityRequirement(name = "cookieAuth")
  @StandardErrorResponses
  @GetMapping("/me")
  public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
    AuthResponse response = authService.getCurrentUser(userDetails.publicId());
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "닉네임 변경")
  @SecurityRequirement(name = "cookieAuth")
  @StandardErrorResponses
  @PatchMapping("/nickname")
  public ResponseEntity<AuthResponse> changeNickname(
      @Valid @RequestBody NicknameUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    AuthResponse response = authService.changeNickname(userDetails.publicId(), request.nickname());
    authService.syncNicknameToRedis(userDetails.publicId(), response.nickname());
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "프로필 이미지 업로드", description = "WebP 형식, 최대 1MB")
  @SecurityRequirement(name = "cookieAuth")
  @StandardErrorResponses
  @PatchMapping("/profile-image")
  public ResponseEntity<AuthResponse> uploadProfileImage(
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    String profileUrl = profileImageService.save(userDetails.publicId(), file);
    AuthResponse response = authService.updateProfileUrl(userDetails.publicId(), profileUrl);
    authService.syncProfileUrlToRedis(userDetails.publicId(), response.profileUrl());
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "프로필 이미지 삭제")
  @SecurityRequirement(name = "cookieAuth")
  @StandardErrorResponses
  @DeleteMapping("/profile-image")
  public ResponseEntity<AuthResponse> deleteProfileImage(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    AuthResponse response = authService.updateProfileUrl(userDetails.publicId(), null);
    profileImageService.delete(userDetails.publicId());
    authService.syncProfileUrlToRedis(userDetails.publicId(), null);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "회원 탈퇴")
  @SecurityRequirement(name = "cookieAuth")
  @StandardErrorResponses
  @DeleteMapping("/account")
  public ResponseEntity<Void> deleteAccount(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @CookieValue(value = "access_token", required = false) String accessToken) {
    accountService.deleteAccount(userDetails.publicId());
    profileImageService.delete(userDetails.publicId());
    accountService.cleanupRedis(userDetails.publicId(), accessToken);

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteAccessTokenCookie().toString())
        .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteRefreshTokenCookie().toString())
        .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteSessionIndicatorCookie().toString())
        .build();
  }

  private ResponseEntity<AuthResponse> issueTokensAndRespond(Member member, HttpStatus status) {
    TokenPair tokenPair = authService.issueTokens(member);

    AuthResponse response =
        new AuthResponse(
            member.getPublicId(),
            member.getNickname(),
            member.getProfileUrl(),
            member.getRole().name());

    return ResponseEntity.status(status)
        .header(
            HttpHeaders.SET_COOKIE,
            cookieUtils.createAccessTokenCookie(tokenPair.accessToken()).toString())
        .header(
            HttpHeaders.SET_COOKIE,
            cookieUtils.createRefreshTokenCookie(tokenPair.refreshToken()).toString())
        .header(HttpHeaders.SET_COOKIE, cookieUtils.createSessionIndicatorCookie().toString())
        .body(response);
  }
}
