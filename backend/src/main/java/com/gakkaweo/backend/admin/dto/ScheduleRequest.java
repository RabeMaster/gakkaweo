package com.gakkaweo.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ScheduleRequest(@NotNull LocalDate date) {}
