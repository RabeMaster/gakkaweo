package com.gakkaweo.backend.config;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import com.gakkaweo.backend.auth.config.OAuth2Properties;
import com.gakkaweo.backend.auth.jwt.JwtAuthenticationFilter;
import com.gakkaweo.backend.auth.oauth2.CookieAuthorizationRequestRepository;
import com.gakkaweo.backend.auth.oauth2.handler.OAuth2LoginFailureHandler;
import com.gakkaweo.backend.auth.oauth2.handler.OAuth2LoginSuccessHandler;
import com.gakkaweo.backend.auth.oauth2.service.CustomOAuth2UserService;
import com.gakkaweo.backend.auth.security.RestAccessDeniedHandler;
import com.gakkaweo.backend.auth.security.RestAuthenticationEntryPoint;
import com.gakkaweo.backend.config.openapi.OpenApiProperties;
import com.gakkaweo.backend.ratelimit.filter.RateLimitFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RateLimitFilter rateLimitFilter;
  private final CustomOAuth2UserService customOAuth2UserService;
  private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
  private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
  private final CookieAuthorizationRequestRepository authorizationRequestRepository;
  private final OAuth2Properties oAuth2Properties;
  private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
  private final RestAccessDeniedHandler restAccessDeniedHandler;
  private final OpenApiProperties openApiProperties;

  @Bean
  static RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.withDefaultRolePrefix()
        .role("SUPERADMIN")
        .implies("ADMIN")
        .role("ADMIN")
        .implies("USER")
        .build();
  }

  @Bean
  static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      RoleHierarchy roleHierarchy) {
    DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
    handler.setRoleHierarchy(roleHierarchy);
    return handler;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/health", "/auth/refresh", "/auth/register", "/auth/login")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers("/uploads/**")
                    .permitAll()
                    .requestMatchers("/v3/api-docs")
                    .denyAll()
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/public",
                        "/v3/api-docs/swagger-config",
                        "/swagger-resources/**",
                        "/webjars/**")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/admin")
                    .hasRole("ADMIN")
                    .requestMatchers("/v3/api-docs/full")
                    .access(
                        (authentication, context) ->
                            new AuthorizationDecision(openApiProperties.docsMode()))
                    .requestMatchers(HttpMethod.GET, "/daily/today")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/daily/guess")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/ranking/today")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/ranking/stream")
                    .permitAll()
                    .requestMatchers("/announcements/active")
                    .permitAll()
                    // SUPERADMIN 전용 (회복 불가/운영·보안 영향 큰 액션). /admin/** 매처보다 먼저 평가되어야 함
                    .requestMatchers(HttpMethod.DELETE, "/admin/users/*")
                    .hasRole("SUPERADMIN")
                    .requestMatchers(HttpMethod.POST, "/admin/sentences/emergency-replace")
                    .hasRole("SUPERADMIN")
                    .requestMatchers(HttpMethod.POST, "/admin/system/ranking-cache/reset")
                    .hasRole("SUPERADMIN")
                    .requestMatchers(HttpMethod.POST, "/admin/system/rate-limit/reset")
                    .hasRole("SUPERADMIN")
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/daily/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(restAuthenticationEntryPoint)
                    .accessDeniedHandler(restAccessDeniedHandler))
        .oauth2Login(
            oauth2 ->
                oauth2
                    .authorizationEndpoint(
                        e -> e.authorizationRequestRepository(authorizationRequestRepository))
                    .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(oAuth2Properties.authorizedRedirectUri()));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    configuration.setExposedHeaders(List.of("Retry-After", "X-Rate-Limit-Remaining"));
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
