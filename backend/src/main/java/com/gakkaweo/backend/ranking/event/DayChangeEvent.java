package com.gakkaweo.backend.ranking.event;

import java.util.UUID;

public record DayChangeEvent(UUID newSentenceId) {}
