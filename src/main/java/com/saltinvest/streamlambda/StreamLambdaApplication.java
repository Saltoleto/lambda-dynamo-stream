package com.saltinvest.streamlambda;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Spring Boot application used only as an IoC container for the Lambda.
 * No web server is started (WebApplicationType.NONE).
 */
@SpringBootApplication
public class StreamLambdaApplication {

    /**
     * For local manual run/debugging (not used by AWS Lambda).
     */
    public static void main(String[] args) {
        new SpringApplicationBuilder(StreamLambdaApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
