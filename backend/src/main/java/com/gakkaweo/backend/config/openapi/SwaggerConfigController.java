package com.gakkaweo.backend.config.openapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwaggerConfigController {

  private final SwaggerUiConfigParameters configParameters;

  public SwaggerConfigController(SwaggerUiConfigParameters configParameters) {
    this.configParameters = configParameters;
  }

  @GetMapping("/v3/api-docs/swagger-config")
  public Map<String, Object> swaggerConfig() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("configUrl", "/v3/api-docs/swagger-config");
    config.put("urls", filterUrlsByRole());
    config.put("operationsSorter", configParameters.getOperationsSorter());
    config.put("tagsSorter", configParameters.getTagsSorter());
    config.put("validatorUrl", "");
    return config;
  }

  private List<Map<String, String>> filterUrlsByRole() {
    Set<org.springdoc.core.properties.SwaggerUiConfigParameters.SwaggerUrl> urls =
        configParameters.getUrls();
    boolean isAdmin = hasAdminRole();

    return urls.stream()
        .filter(
            url -> {
              String name = url.getName();
              if ("full".equals(name)) {
                return false;
              }
              if ("admin".equals(name)) {
                return isAdmin;
              }
              return true;
            })
        .map(
            url -> {
              Map<String, String> entry = new LinkedHashMap<>();
              entry.put("url", url.getUrl());
              entry.put(
                  "name", url.getDisplayName() != null ? url.getDisplayName() : url.getName());
              return entry;
            })
        .toList();
  }

  private boolean hasAdminRole() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch("ROLE_ADMIN"::equals);
  }
}
