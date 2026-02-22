package com.saltinvest.streamlambda.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.saltinvest.streamlambda.service.dto.DynamoChange;
import com.saltinvest.streamlambda.service.dto.DynamoChangeBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Converts DynamoDB Streams events into a compact JSON payload and forwards it to a REST endpoint.
 *
 * Recommended: keep this processor idempotent (use eventId) because Streams can retry.
 */
@Service
public class DynamoStreamProcessor {

    private static final Logger log = LoggerFactory.getLogger(DynamoStreamProcessor.class);

    private final RestNotifyService restNotifyService;

    public DynamoStreamProcessor(RestNotifyService restNotifyService) {
        this.restNotifyService = restNotifyService;
    }

    public void process(DynamodbEvent event, Context ctx) {
        if (event == null || event.getRecords() == null || event.getRecords().isEmpty()) {
            log.info("Empty event; nothing to do.");
            return;
        }

        List<DynamoChange> changes = new ArrayList<>();
        for (DynamodbEvent.DynamodbStreamRecord r : event.getRecords()) {
            try {
                changes.add(toChange(r));
            } catch (Exception e) {
                // Fail fast to allow Lambda retries (event source mapping handles retries).
                log.error("Failed to map record to change. eventID={}", safeEventId(r), e);
                throw e;
            }
        }

        DynamoChangeBatch batch = new DynamoChangeBatch(
                Instant.now().toString(),
                ctx != null ? ctx.getAwsRequestId() : null,
                changes
        );

        // A single outbound call per batch is typically cheaper and faster than one call per record.
        restNotifyService.notify(batch);
    }

    private DynamoChange toChange(DynamodbEvent.DynamodbStreamRecord r) {
        String eventId = r.getEventID();
        String eventName = r.getEventName();
        String eventSourceArn = r.getEventSourceARN();
        String approxCreatedAt = r.getDynamodb() != null && r.getDynamodb().getApproximateCreationDateTime() != null
                ? r.getDynamodb().getApproximateCreationDateTime().toInstant().toString()
                : null;

        Map<String, Object> keys = convertMap(r.getDynamodb() != null ? r.getDynamodb().getKeys() : null);
        Map<String, Object> newImage = convertMap(r.getDynamodb() != null ? r.getDynamodb().getNewImage() : null);
        Map<String, Object> oldImage = convertMap(r.getDynamodb() != null ? r.getDynamodb().getOldImage() : null);

        return new DynamoChange(eventId, eventName, eventSourceArn, approxCreatedAt, keys, newImage, oldImage);
    }

    private String safeEventId(DynamodbEvent.DynamodbStreamRecord r) {
        try { return r.getEventID(); } catch (Exception e) { return null; }
    }

    private Map<String, Object> convertMap(Map<String, AttributeValue> in) {
        if (in == null || in.isEmpty()) return Collections.emptyMap();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, AttributeValue> e : in.entrySet()) {
            out.put(e.getKey(), convertValue(e.getValue()));
        }
        return out;
    }

    private Object convertValue(AttributeValue v) {
        if (v == null) return null;

        // Scalar types
        if (v.getS() != null) return v.getS();
        if (v.getN() != null) {
            // keep numeric as String-safe; downstream may parse
            return v.getN();
        }
        if (v.getBOOL() != null) return v.getBOOL();
        if (v.getNULL() != null && v.getNULL()) return null;
        if (v.getB() != null) return Base64.getEncoder().encodeToString(v.getB().array());

        // Set types
        if (v.getSS() != null) return new ArrayList<>(v.getSS());
        if (v.getNS() != null) return new ArrayList<>(v.getNS());
        if (v.getBS() != null) {
            List<String> bs = new ArrayList<>();
            for (java.nio.ByteBuffer b : v.getBS()) {
                bs.add(Base64.getEncoder().encodeToString(b.array()));
            }
            return bs;
        }

        // Complex types
        if (v.getM() != null) return convertMap(v.getM());
        if (v.getL() != null) {
            List<Object> list = new ArrayList<>();
            for (AttributeValue item : v.getL()) list.add(convertValue(item));
            return list;
        }

        // Fallback
        return v.toString();
    }
}
