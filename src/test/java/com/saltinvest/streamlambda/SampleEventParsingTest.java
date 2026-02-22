package com.saltinvest.streamlambda;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SampleEventParsingTest {

    @Test
    void shouldParseSampleEvent() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("dynamodb-stream-sample.json")) {
            assertThat(is).isNotNull();
            DynamodbEvent event = mapper.readValue(is, DynamodbEvent.class);
            assertThat(event.getRecords()).isNotEmpty();
            assertThat(event.getRecords().get(0).getEventName()).isEqualTo("INSERT");
        }
    }
}
