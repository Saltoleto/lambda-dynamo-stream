package com.saltinvest.streamlambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.saltinvest.streamlambda.StreamLambdaApplication;
import com.saltinvest.streamlambda.service.DynamoStreamProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * AWS Lambda handler for DynamoDB Streams.
 *
 * Important for performance:
 * - The Spring context is created once and cached in a static field.
 * - With SnapStart enabled, the initialized JVM + Spring context are snapshotted at publish time,
 *   significantly reducing cold starts.
 */
public class DynamoStreamHandler implements RequestHandler<DynamodbEvent, Void> {

    private static final Logger log = LoggerFactory.getLogger(DynamoStreamHandler.class);

    private static final ConfigurableApplicationContext CTX = new SpringApplicationBuilder(StreamLambdaApplication.class)
            .web(WebApplicationType.NONE)
            .lazyInitialization(true)
            .run();

    private final DynamoStreamProcessor processor;

    public DynamoStreamHandler() {
        this.processor = CTX.getBean(DynamoStreamProcessor.class);
    }

    @Override
    public Void handleRequest(DynamodbEvent event, Context context) {
        int records = (event == null || event.getRecords() == null) ? 0 : event.getRecords().size();
        log.info("Received DynamoDB Stream event with {} record(s)", records);
        processor.process(event, context);
        return null;
    }
}
