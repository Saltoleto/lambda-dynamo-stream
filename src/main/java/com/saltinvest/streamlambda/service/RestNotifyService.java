package com.saltinvest.streamlambda.service;

import com.saltinvest.streamlambda.security.StsTokenService;
import com.saltinvest.streamlambda.service.dto.DynamoChangeBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Calls an external REST endpoint with the Stream payload.
 *
 * Auth:
 * - Gets a client_credentials token (STS_TOKEN_URL) using client_id/client_secret stored in Secrets Manager.
 * - Adds header: Authorization: Bearer <token>
 *
 * Environment variables (recommended):
 * - TARGET_URL: required
 * - CLIENT_CREDENTIALS_SECRET_NAME: required (Secrets Manager secret name)
 * - STS_TOKEN_URL: required (token endpoint)
 * - STS_SCOPE: optional
 * - TARGET_API_KEY: optional (sent as header X-API-Key)
 */
@Service
public class RestNotifyService {

    private static final Logger log = LoggerFactory.getLogger(RestNotifyService.class);

    private final RestClient restClient;
    private final StsTokenService tokenService;
    private final String targetUrl;
    private final String apiKey;

    public RestNotifyService(
            RestClient restClient,
            StsTokenService tokenService,
            @Value("${target.url:${TARGET_URL:}}") String targetUrl,
            @Value("${target.api-key:${TARGET_API_KEY:}}") String apiKey
    ) {
        this.restClient = restClient;
        this.tokenService = tokenService;
        this.targetUrl = targetUrl;
        this.apiKey = apiKey;
    }

    public void notify(DynamoChangeBatch payload) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalStateException("Missing TARGET_URL (or target.url). Configure it as an environment variable or application property.");
        }

        String token = tokenService.getAccessToken();

        log.info("Calling REST target: {}", targetUrl);

        try {
            restClient.post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Source", "dynamodb-stream-lambda")
                    .header("Authorization", "Bearer " + token)
                    .headers(h -> {
                        if (apiKey != null && !apiKey.isBlank()) {
                            h.add("X-API-Key", apiKey);
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("REST call completed successfully.");
        } catch (Exception ex) {
            // Throw to make Lambda fail -> Streams mapping retries (at-least-once delivery).
            log.error("REST call failed. Will trigger retry from event source mapping.", ex);
            throw ex;
        }
    }
}
