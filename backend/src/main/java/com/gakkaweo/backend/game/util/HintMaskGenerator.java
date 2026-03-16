package com.gakkaweo.backend.game.util;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HintMaskGenerator {

  public HintMask generate(String sentence) {
    String[] words = sentence.split("\\s+");
    List<Integer> charCounts = new ArrayList<>();
    StringBuilder mask = new StringBuilder();

    for (int i = 0; i < words.length; i++) {
      int length = words[i].length();
      charCounts.add(length);
      mask.append("_".repeat(length));
      if (i < words.length - 1) {
        mask.append(" ");
      }
    }

    return new HintMask(mask.toString(), charCounts);
  }

  public record HintMask(String mask, List<Integer> charCounts) {}
}
