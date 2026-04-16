package com.gakkaweo.backend.config.openapi;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequiredArgsConstructor
public class SwaggerConfigController {

  private final List<GroupedOpenApi> groupedOpenApis;
  private final SwaggerUiConfigProperties swaggerUiConfigProperties;

  @GetMapping("/v3/api-docs/swagger-config")
  public Map<String, Object> swaggerConfig() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("configUrl", "/v3/api-docs/swagger-config");
    config.put("urls", filterUrlsByRole());
    config.put("operationsSorter", swaggerUiConfigProperties.getOperationsSorter());
    config.put("tagsSorter", swaggerUiConfigProperties.getTagsSorter());
    config.put("validatorUrl", "");
    return config;
  }

  private List<Map<String, String>> filterUrlsByRole() {
    boolean isAdmin = hasAdminRole();

    return groupedOpenApis.stream()
        .filter(
            group -> {
              String name = group.getGroup();
              if ("full".equals(name)) {
                return false;
              }
              if ("admin".equals(name)) {
                return isAdmin;
              }
              return true;
            })
        .map(
            group -> {
              Map<String, String> entry = new LinkedHashMap<>();
              entry.put("url", "/v3/api-docs/" + group.getGroup());
              entry.put("name", group.getDisplayName());
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
