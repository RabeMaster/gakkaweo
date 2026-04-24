package com.gakkaweo.backend.admin.service;

import com.gakkaweo.backend.admin.dto.CsvUploadResponse;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.admin.entity.SentenceUpload;
import com.gakkaweo.backend.domain.admin.repository.SentenceUploadRepository;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class CsvUploadService {

  private final DailySentenceRepository dailySentenceRepository;
  private final SentenceUploadRepository sentenceUploadRepository;
  private final MemberRepository memberRepository;
  private final AdminAuditService adminAuditService;
  private final TransactionTemplate transactionTemplate;

  public CsvUploadResponse uploadCsv(MultipartFile file, UUID adminPublicId, String ipAddress) {
    Member admin =
        memberRepository
            .findByPublicId(adminPublicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    List<String> lines = parseCsv(file);
    String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";

    return transactionTemplate.execute(
        status -> {
          int successCount = 0;
          int duplicateCount = 0;
          List<DailySentence> newSentences = new ArrayList<>();

          for (String line : lines) {
            String sentence = line.strip();
            if (sentence.isEmpty()) {
              continue;
            }
            if (dailySentenceRepository.existsBySentence(sentence)) {
              duplicateCount++;
              continue;
            }
            newSentences.add(new DailySentence(sentence));
            successCount++;
          }

          if (!newSentences.isEmpty()) {
            dailySentenceRepository.saveAll(newSentences);
          }

          sentenceUploadRepository.save(new SentenceUpload(admin, fileName, successCount));

          log.info(
              "CSV 업로드 완료: fileName={}, total={}, success={}, duplicate={}",
              fileName,
              lines.size(),
              successCount,
              duplicateCount);

          adminAuditService.log(
              admin,
              "CSV_UPLOAD",
              "SENTENCE",
              null,
              "success=" + successCount + ", duplicate=" + duplicateCount,
              ipAddress);

          return new CsvUploadResponse(lines.size(), successCount, duplicateCount);
        });
  }

  private List<String> parseCsv(MultipartFile file) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      List<String> lines = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.strip();
        if (!trimmed.isEmpty()) {
          lines.add(trimmed);
        }
      }
      return lines;
    } catch (Exception e) {
      log.error("CSV 파싱 실패: {}", e.getMessage(), e);
      throw new BusinessException(ErrorCode.CSV_PARSE_ERROR);
    }
  }
}
