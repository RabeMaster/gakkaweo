package com.gakkaweo.backend.config;

import com.gakkaweo.backend.member.config.ProfileImageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final ProfileImageProperties profileImageProperties;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/uploads/profiles/**")
        .addResourceLocations("file:" + profileImageProperties.profileDir() + "/")
        .setCachePeriod(31536000);
  }
}
