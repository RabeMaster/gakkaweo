package com.gakkaweo.backend.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GuessRequest(
    @NotNull UUID sentenceId, @NotBlank @Size(min = 2, max = 200) String guessText) {}
