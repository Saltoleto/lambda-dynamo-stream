package com.saltinvest.streamlambda.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient tuned for Lambda:
 * - timeouts avoid hanging invocations
 * - lightweight HTTP stack (JDK HttpURLConnection via SimpleClientHttpRequestFactory)
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(5_000);

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
