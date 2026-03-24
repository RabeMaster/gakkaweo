package com.gakkaweo.backend.config;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import com.gakkaweo.backend.auth.config.OAuth2Properties;
import com.gakkaweo.backend.auth.jwt.JwtAuthenticationFilter;
import com.gakkaweo.backend.auth.oauth2.CookieAuthorizationRequestRepository;
import com.gakkaweo.backend.auth.oauth2.handler.OAuth2LoginFailureHandler;
import com.gakkaweo.backend.auth.oauth2.handler.OAuth2LoginSuccessHandler;
import com.gakkaweo.backend.auth.oauth2.service.CustomOAuth2UserService;
import com.gakkaweo.backend.auth.security.RestAuthenticationEntryPoint;
import com.gakkaweo.backend.ratelimit.filter.RateLimitFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/health", "/auth/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/daily/today")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/daily/guess")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/ranking/today")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/ranking/stream")
                    .permitAll()
                    .requestMatchers("/daily/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
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
    configuration.setAllowedOrigins(List.of(oAuth2Properties.getAuthorizedRedirectUri()));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    configuration.setExposedHeaders(List.of("Retry-After", "X-Rate-Limit-Remaining"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
