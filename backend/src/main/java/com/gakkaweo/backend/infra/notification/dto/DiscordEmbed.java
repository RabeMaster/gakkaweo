package com.gakkaweo.backend.infra.notification.dto;

import java.util.List;

public record DiscordEmbed(String title, String description, Integer color, List<Field> fields) {

  public record Field(String name, String value, boolean inline) {}
}
