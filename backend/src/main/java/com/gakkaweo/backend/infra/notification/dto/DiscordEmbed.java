package com.gakkaweo.backend.infra.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiscordEmbed(
    String title, String description, Integer color, List<Field> fields, String timestamp) {

  public DiscordEmbed(String title, String description, Integer color, List<Field> fields) {
    this(title, description, color, fields, null);
  }

  public record Field(String name, String value, boolean inline) {}
}
