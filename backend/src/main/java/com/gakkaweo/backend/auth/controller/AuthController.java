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
import com.gakkaweo.backend.auth.util.CookieUtils;
import com.gakkaweo.backend.domain.member.entity.Member;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final LocalAuthService localAuthService;
  private final AccountService accountService;
  private final CookieUtils cookieUtils;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    Member member = localAuthService.register(request.username(), request.password());
    TokenPair tokenPair = authService.issueTokens(member);

    AuthResponse response =
        new AuthResponse(
            member.getPublicId(),
            member.getNickname(),
            member.getProfileUrl(),
            member.getRole().name());

    return ResponseEntity.status(201)
        .header(
            HttpHeaders.SET_COOKIE,
            cookieUtils.createAccessTokenCookie(tokenPair.accessToken()).toString())
        .header(
            HttpHeaders.SET_COOKIE,
            cookieUtils.createRefreshTokenCookie(tokenPair.refreshToken()).toString())
        .body(response);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    Member member = localAuthService.authenticate(request.username(), request.password());
    TokenPair tokenPair = authService.issueTokens(member);

    AuthResponse response =
        new AuthResponse(
            member.getPublicId(),
            member.getNickname(),
            member.getProfileUrl(),
            member.getRole().name());

    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            cookieUtils.createAccessTokenCookie(tokenPair.accessToken()).toString())
        .header(
            HttpHeaders.SET_COOKIE,
            cookieUtils.createRefreshTokenCookie(tokenPair.refreshToken()).toString())
        .body(response);
  }

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
        .build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(value = "access_token", required = false) String accessToken) {
    if (accessToken != null) {
      authService.logout(accessToken);
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteAccessTokenCookie().toString())
        .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteRefreshTokenCookie().toString())
        .build();
  }

  @GetMapping("/me")
  public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
    AuthResponse response = authService.getCurrentUser(userDetails.publicId());
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/nickname")
  public ResponseEntity<AuthResponse> changeNickname(
      @Valid @RequestBody NicknameUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    AuthResponse response = authService.changeNickname(userDetails.publicId(), request.nickname());
    authService.syncNicknameToRedis(userDetails.publicId(), response.nickname());
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/account")
  public ResponseEntity<Void> deleteAccount(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @CookieValue(value = "access_token", required = false) String accessToken) {
    accountService.deleteAccount(userDetails.publicId());
    accountService.cleanupRedis(userDetails.publicId(), accessToken);

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteAccessTokenCookie().toString())
        .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteRefreshTokenCookie().toString())
        .build();
  }
}
