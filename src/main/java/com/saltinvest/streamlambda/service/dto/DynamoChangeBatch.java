package com.saltinvest.streamlambda.service.dto;

import java.util.List;

public record DynamoChangeBatch(
        String receivedAt,
        String awsRequestId,
        List<DynamoChange> records
) {}
