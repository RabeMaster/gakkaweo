package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EmergencyReplaceRequest(@NotNull UUID newSentencePublicId, boolean returnOldToPool) {}
