package com.gakkaweo.backend.config.openapi;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
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
  private final RoleHierarchy roleHierarchy;

  @GetMapping("/v3/api-docs/swagger-config")
  public ResponseEntity<SwaggerConfig> swaggerConfig() {
    SwaggerConfig config =
        new SwaggerConfig(
            "/v3/api-docs/swagger-config",
            filterUrlsByRole(),
            swaggerUiConfigProperties.getOperationsSorter(),
            swaggerUiConfigProperties.getTagsSorter(),
            "");
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(config);
  }

  private List<SwaggerConfig.SwaggerUrl> filterUrlsByRole() {
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
            group ->
                new SwaggerConfig.SwaggerUrl(
                    "/v3/api-docs/" + group.getGroup(), group.getDisplayName()))
        .toList();
  }

  private boolean hasAdminRole() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    return roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities()).stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch("ROLE_ADMIN"::equals);
  }

  public record SwaggerConfig(
      String configUrl,
      List<SwaggerUrl> urls,
      String operationsSorter,
      String tagsSorter,
      String validatorUrl) {

    public record SwaggerUrl(String url, String name) {}
  }
}
