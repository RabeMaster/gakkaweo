package com.gakkaweo.backend.infra.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiServiceConfig {

  @Bean
  RestClient aiServiceRestClient(AiServiceProperties properties) {
    int timeoutMillis = (int) properties.getTimeout().toMillis();

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(timeoutMillis);
    factory.setReadTimeout(timeoutMillis);

    return RestClient.builder().baseUrl(properties.getUrl()).requestFactory(factory).build();
  }
}
