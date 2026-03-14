package com.gakkaweo.backend.infra.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class TextNormalizer {

  public String normalize(String text) {
    String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
    String cleaned = nfc.replaceAll("[^가-힣a-zA-Z0-9\\s]", "");
    String collapsed = cleaned.replaceAll("\\s+", " ");
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
