package com.gakkaweo.backend.auth.service;

import com.gakkaweo.backend.auth.config.ProfileImageProperties;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileImageService {

  private static final byte[] RIFF_HEADER = {'R', 'I', 'F', 'F'};
  private static final byte[] WEBP_SIGNATURE = {'W', 'E', 'B', 'P'};

  private final ProfileImageProperties properties;
  private Path uploadDir;

  @PostConstruct
  void init() {
    uploadDir = Path.of(properties.profileDir());
    try {
      Files.createDirectories(uploadDir);
      log.info("프로필 이미지 업로드 디렉토리 준비 완료: {}", uploadDir.toAbsolutePath());
    } catch (IOException e) {
      throw new IllegalStateException("업로드 디렉토리 생성 실패: " + uploadDir, e);
    }
  }

  public String save(UUID publicId, MultipartFile file) {
    validateFile(file);

    String filename = publicId + ".webp";
    Path target = uploadDir.resolve(filename);

    try {
      Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      log.error("프로필 이미지 저장 실패: publicId={}", publicId, e);
      throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
    }

    String url = "/uploads/profiles/" + filename + "?v=" + System.currentTimeMillis();
    log.info("프로필 이미지 저장: publicId={}, path={}", publicId, target);
    return url;
  }

  public void delete(UUID publicId) {
    Path target = uploadDir.resolve(publicId + ".webp");
    try {
      boolean deleted = Files.deleteIfExists(target);
      if (deleted) {
        log.info("프로필 이미지 삭제: publicId={}", publicId);
      }
    } catch (IOException e) {
      log.warn("프로필 이미지 삭제 실패: publicId={}", publicId, e);
    }
  }

  private void validateFile(MultipartFile file) {
    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
      throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
    }

    try (InputStream is = file.getInputStream()) {
      byte[] header = is.readNBytes(12);
      if (header.length < 12) {
        throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
      }

      if (!startsWith(header, 0, RIFF_HEADER) || !startsWith(header, 8, WEBP_SIGNATURE)) {
        throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
    }
  }

  private boolean startsWith(byte[] data, int offset, byte[] prefix) {
    for (int i = 0; i < prefix.length; i++) {
      if (data[offset + i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
