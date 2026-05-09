package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.auth.metrics.AuthMetrics;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.member.entity.LocalAccount;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.service.NicknameGenerator;
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

  private final LocalAccountRepository localAccountRepository;
  private final MemberRepository memberRepository;
  private final NicknameGenerator nicknameGenerator;
  private final PasswordEncoder passwordEncoder;
  private final AuthMetrics authMetrics;
  private final TransactionTemplate transactionTemplate;

  public Member register(String username, String password) {
    if (username.length() < 4 || password.length() < 8) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    if (localAccountRepository.existsByUsernameIgnoreCase(username)) {
      throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
    }

    String passwordHash = passwordEncoder.encode(password);

    Member member =
        transactionTemplate.execute(
            status -> {
              String nickname = nicknameGenerator.generate();
              Member saved = memberRepository.save(new Member(nickname));
              try {
                localAccountRepository.save(new LocalAccount(saved, username, passwordHash));
              } catch (DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
              }
              return saved;
            });

    log.info("로컬 계정 가입: publicId={}, username={}", member.getPublicId(), username);
    authMetrics.recordRegister("local");
    return member;
  }

  public Member authenticate(String username, String password) {
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

    authMetrics.recordLogin("local", true);
    return member;
  }
}
