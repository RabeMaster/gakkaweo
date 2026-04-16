package com.gakkaweo.backend.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("가까워 API")
                .version("1.0.0")
                .description("AI 유사도 기반 단어 게임 REST API")
                .contact(new Contact().name("가까워").url("https://gakkaweo.r4b2.xyz"))
                .license(new License().name("MIT")))
        .addServersItem(new Server().url("https://api.r4b2.xyz").description("Production"))
        .addServersItem(new Server().url("http://localhost:8080").description("Local"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "cookieAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("access_token")
                        .description("JWT access token (HttpOnly Cookie)")));
  }

  @Bean
  public GroupedOpenApi publicGroup() {
    return GroupedOpenApi.builder()
        .group("public")
        .displayName("Public API")
        .pathsToMatch(
            "/health",
            "/auth/**",
            "/daily/**",
            "/ranking/**",
            "/announcements/**",
            "/oauth2/authorization/**",
            "/login/oauth2/code/**")
        .addOpenApiCustomizer(oauthPathsCustomizer())
        .build();
  }

  @Bean
  public OpenApiCustomizer oauthPathsCustomizer() {
    return openApi -> {
      Parameter registrationIdParam =
          new Parameter()
              .name("registrationId")
              .in("path")
              .required(true)
              .schema(
                  new StringSchema()._enum(List.of("kakao", "google", "naver"))._default("kakao"))
              .description("OAuth 프로바이더");

      openApi
          .path(
              "/oauth2/authorization/{registrationId}",
              new PathItem()
                  .get(
                      new Operation()
                          .addTagsItem("Auth")
                          .summary("OAuth 소셜 로그인 시작")
                          .description("선택한 프로바이더 인증 페이지로 302 리다이렉트")
                          .addParametersItem(registrationIdParam)
                          .responses(
                              new ApiResponses()
                                  .addApiResponse(
                                      "302",
                                      new ApiResponse().description("프로바이더 인증 페이지로 리다이렉트")))))
          .path(
              "/login/oauth2/code/{registrationId}",
              new PathItem()
                  .get(
                      new Operation()
                          .addTagsItem("Auth")
                          .summary("OAuth 콜백 (Spring Security 자동 처리)")
                          .description(
                              "프로바이더 인증 완료 후 콜백. "
                                  + "성공 시 JWT 쿠키 발급 + 프론트엔드 리다이렉트. "
                                  + "이 엔드포인트는 직접 호출하지 않음")
                          .addParametersItem(registrationIdParam)
                          .responses(
                              new ApiResponses()
                                  .addApiResponse(
                                      "302",
                                      new ApiResponse()
                                          .description("프론트엔드로 리다이렉트 + Set-Cookie")))));
    };
  }

  @Bean
  public GroupedOpenApi adminGroup() {
    return GroupedOpenApi.builder()
        .group("admin")
        .displayName("Admin API")
        .pathsToMatch("/admin/**")
        .build();
  }

  @Bean
  @ConditionalOnProperty(name = "app.openapi.docs-mode", havingValue = "true")
  public GroupedOpenApi fullGroup() {
    return GroupedOpenApi.builder()
        .group("full")
        .displayName("Full API (CI)")
        .pathsToMatch("/**")
        .build();
  }
}
