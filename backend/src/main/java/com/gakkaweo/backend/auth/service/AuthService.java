package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.auth.dto.AuthResponse;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.LocalAccountRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.domain.member.validation.NicknameValidator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private final MemberRepository memberRepository;
  private final LocalAccountRepository localAccountRepository;
  private final NicknameValidator nicknameValidator;

  @Transactional(readOnly = true)
  public AuthResponse getCurrentUser(UUID publicId) {
    Member member =
        memberRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    return responseFrom(member);
  }

  @Transactional
  public AuthResponse changeNickname(UUID publicId, String rawNickname) {
    String nickname = nicknameValidator.normalize(rawNickname);
    nicknameValidator.validate(nickname);

    Member member =
        memberRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    if (member.getNickname().equals(nickname)) {
      throw new BusinessException(ErrorCode.NICKNAME_UNCHANGED);
    }

    if (memberRepository.existsByNickname(nickname)) {
      throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
    }

    member.setNickname(nickname);

    try {
      memberRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
    }

    return responseFrom(member);
  }

  @Transactional
  public AuthResponse updateProfileUrl(UUID publicId, String profileUrl) {
    Member member =
        memberRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    member.setProfileUrl(profileUrl);

    return responseFrom(member);
  }

  public AuthResponse responseFrom(Member member) {
    return AuthResponse.from(member, localAccountRepository.existsByMember(member));
  }
}
