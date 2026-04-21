package com.gakkaweo.backend.infra.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiscordWebhookPayload(
    String content,
    List<DiscordEmbed> embeds,
    @JsonProperty("allowed_mentions") AllowedMentions allowedMentions) {}
