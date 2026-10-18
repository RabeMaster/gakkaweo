package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.metrics.AuthMetrics;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.member.entity.LocalAccount;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.service.NicknameGenerator;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocalAuthService {

  private static final int PASSWORD_MIN_LENGTH = 8;
  private static final int PASSWORD_MAX_BYTES = 72;

  private final LocalAccountRepository localAccountRepository;
  private final MemberRepository memberRepository;
  private final NicknameGenerator nicknameGenerator;
  private final PasswordEncoder passwordEncoder;
  private final AuthMetrics authMetrics;
  private final TransactionTemplate transactionTemplate;
  private final AuthSessionService authSessionService;

  private static boolean isPasswordTooLong(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES;
  }

  private static void validateNewPassword(String password) {
    if (password.length() < PASSWORD_MIN_LENGTH || isPasswordTooLong(password)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
  }

  public LoginResult registerAndLogin(String username, String password) {
    String hash = prepareRegistration(username, password);

    LoginResult result =
        transactionTemplate.execute(
            status -> {
              Member member = createAccount(username, hash);

              return new LoginResult(member, authSessionService.issue(member.getId()));
            });

    recordRegistration(result.member(), username);

    return result;
  }

  private String prepareRegistration(String username, String password) {
    if (username.length() < 4) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    validateNewPassword(password);

    if (localAccountRepository.existsByUsernameIgnoreCase(username)) {
      throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
    }

    return passwordEncoder.encode(password);
  }

  private Member createAccount(String username, String hash) {
    Member member = memberRepository.save(new Member(nicknameGenerator.generate()));

    try {
      localAccountRepository.save(new LocalAccount(member, username, hash));
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
    }

    return member;
  }

  private void recordRegistration(Member member, String username) {
    log.info("로컬 계정 가입: publicId={}, username={}", member.getPublicId(), username);
    authMetrics.recordRegister("local");
  }

  public LoginResult login(String username, String password) {
    LocalAccount snapshot = authenticateAccount(username, password);

    LoginResult result =
        transactionTemplate.execute(
            status -> {
              Member member =
                  memberRepository
                      .findByIdForUpdate(snapshot.getMember().getId())
                      .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

              if (Boolean.TRUE.equals(member.getBanned())) {
                throw new BusinessException(ErrorCode.MEMBER_BANNED);
              }

              LocalAccount account =
                  localAccountRepository
                      .findByMemberId(member.getId())
                      .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

              if (!account.getPasswordHash().equals(snapshot.getPasswordHash())) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
              }

              return new LoginResult(member, authSessionService.issue(member.getId()));
            });

    authMetrics.recordLogin("local", true);

    return result;
  }

  private LocalAccount authenticateAccount(String username, String password) {
    if (isPasswordTooLong(password)) {
      authMetrics.recordLogin("local", false);
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    LocalAccount localAccount =
        localAccountRepository
            .findByUsernameIgnoreCase(username)
            .orElseThrow(
                () -> {
                  authMetrics.recordLogin("local", false);
                  return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

    if (!passwordEncoder.matches(password, localAccount.getPasswordHash())) {
      authMetrics.recordLogin("local", false);
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    Member member = localAccount.getMember();

    if (Boolean.TRUE.equals(member.getBanned())) {
      authMetrics.recordLogin("local", false);
      throw new BusinessException(ErrorCode.MEMBER_BANNED);
    }

    return localAccount;
  }

  public void changePassword(UUID publicId, String currentPassword, String newPassword) {
    if (currentPassword.equals(newPassword)) {
      throw new BusinessException(ErrorCode.PASSWORD_UNCHANGED);
    }

    if (isPasswordTooLong(currentPassword)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    validateNewPassword(newPassword);

    LocalAccount snapshot =
        localAccountRepository
            .findByMemberPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LOCAL_ACCOUNT_REQUIRED));

    // BCrypt 연산은 쓰기 트랜잭션 전에 수행하고, 잠금 후 해시 일치 여부를
    // 다시 확인합니다.
    if (!passwordEncoder.matches(currentPassword, snapshot.getPasswordHash())) {
      throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
    }

    String newHash = passwordEncoder.encode(newPassword);

    transactionTemplate.executeWithoutResult(
        status -> {
          Member member =
              memberRepository
                  .findByIdForUpdate(snapshot.getMember().getId())
                  .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

          LocalAccount account =
              localAccountRepository
                  .findByMemberId(member.getId())
                  .orElseThrow(() -> new BusinessException(ErrorCode.LOCAL_ACCOUNT_REQUIRED));

          if (!account.getPasswordHash().equals(snapshot.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
          }

          if (Boolean.TRUE.equals(member.getBanned())) {
            throw new BusinessException(ErrorCode.MEMBER_BANNED);
          }

          account.changePasswordHash(newHash);
          authSessionService.revokeAll(member.getId());
        });
  }

  public record LoginResult(Member member, TokenPair tokens) {}
}
