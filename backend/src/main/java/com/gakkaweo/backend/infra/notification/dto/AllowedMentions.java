package com.gakkaweo.backend.infra.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AllowedMentions(List<String> parse, List<String> roles) {

  public static AllowedMentions none() {
    return new AllowedMentions(List.of(), null);
  }

  public static AllowedMentions roles(List<String> roleIds) {
    return new AllowedMentions(List.of(), roleIds);
  }
}
