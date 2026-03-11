package com.gakkaweo.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

  @GetMapping("/health")
  public String health() {
    System.out.println("Health check endpoint called");
    return "hello";
  }
}
