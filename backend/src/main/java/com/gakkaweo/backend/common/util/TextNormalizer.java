package com.gakkaweo.backend.common.util;

import java.text.Normalizer;
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
    return HashUtils.sha256Hex(normalizedText);
  }
}
