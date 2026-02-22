package com.saltinvest.streamlambda.service.dto;

import java.util.Map;

public record DynamoChange(
        String eventId,
        String eventName,
        String eventSourceArn,
        String approximateCreationDateTime,
        Map<String, Object> keys,
        Map<String, Object> newImage,
        Map<String, Object> oldImage
) {}
