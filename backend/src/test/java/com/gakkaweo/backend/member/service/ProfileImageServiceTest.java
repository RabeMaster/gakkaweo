package com.gakkaweo.backend.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.member.config.ProfileImageProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("프로필 이미지 스트림 종료 단위 테스트")
class ProfileImageServiceTest {

  private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

  @TempDir Path directory;

  @Test
  @DisplayName("프로필 저장 성공 후 검증·복사에 사용한 스트림을 모두 닫는다")
  void 프로필_저장성공_검증_복사스트림_종료() throws Exception {
    var service = new ProfileImageService(new ProfileImageProperties(directory.toString()));
    service.init();

    var closed = new AtomicInteger();
    var file = mock(MultipartFile.class);

    when(file.getContentType()).thenReturn("image/webp");
    when(file.getInputStream())
        .thenAnswer(
            invocation ->
                new ByteArrayInputStream(WEBP) {
                  @Override
                  public void close() throws IOException {
                    closed.incrementAndGet();
                    super.close();
                  }
                });

    assertThat(service.save(UUID.randomUUID(), file)).startsWith("/uploads/profiles/");

    assertThat(closed.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("프로필 복사 중 읽기가 실패해도 복사 스트림을 닫는다")
  void 프로필_복사실패_스트림_종료() throws Exception {
    var service = new ProfileImageService(new ProfileImageProperties(directory.toString()));
    service.init();

    var closed = new AtomicInteger();
    var file = mock(MultipartFile.class);

    when(file.getContentType()).thenReturn("image/webp");
    when(file.getInputStream())
        .thenReturn(
            new ByteArrayInputStream(WEBP),
            new InputStream() {
              @Override
              public int read() throws IOException {
                throw new IOException("copy fixture failure");
              }

              @Override
              public void close() {
                closed.incrementAndGet();
              }
            });

    assertThatThrownBy(() -> service.save(UUID.randomUUID(), file))
        .isInstanceOf(BusinessException.class);

    assertThat(closed.get()).isEqualTo(1);
  }
}
