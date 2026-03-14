package com.gakkaweo.backend.infra.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TextNormalizer {

  private static final Pattern CLEAN_PATTERN = Pattern.compile("[^가-힣a-zA-Z0-9\\s]");
  private static final Pattern COLLAPSE_PATTERN = Pattern.compile("\\s+");

  public String normalize(String text) {
    String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
    String cleaned = CLEAN_PATTERN.matcher(nfc).replaceAll("");
    String collapsed = COLLAPSE_PATTERN.matcher(cleaned).replaceAll(" ");
    return collapsed.strip();
  }

  public String hashForCache(String normalizedText) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(normalizedText.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
    }
  }
}
