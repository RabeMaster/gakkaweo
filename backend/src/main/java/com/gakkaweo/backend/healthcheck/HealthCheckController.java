package com.gakkaweo.backend.healthcheck;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "헬스체크")
public class HealthCheckController {

  @Operation(summary = "헬스체크")
  @GetMapping("/health")
  public String health() {
    return "hello";
  }
}
