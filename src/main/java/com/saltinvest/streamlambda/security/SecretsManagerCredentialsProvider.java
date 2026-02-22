package com.saltinvest.streamlambda.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches client_id and client_secret from AWS Secrets Manager.
 *
 * Secret format (recommended JSON):
 * {
 *   "client_id": "xxx",
 *   "client_secret": "yyy"
 * }
 *
 * Performance:
 * - caches the secret in-memory to avoid a Secrets Manager call on every invoke
 * - secret retrieval is lazy (first use), which keeps init/SnapStart snapshots free of secrets by default
 */
@Service
public class SecretsManagerCredentialsProvider {

    private static final Logger log = LoggerFactory.getLogger(SecretsManagerCredentialsProvider.class);

    private final ObjectMapper objectMapper;
    private final String secretName;

    private final AtomicReference<ClientCredentials> cache = new AtomicReference<>();

    public SecretsManagerCredentialsProvider(
            ObjectMapper objectMapper,
            @Value("${secrets.client-credentials-name:${CLIENT_CREDENTIALS_SECRET_NAME:}}") String secretName
    ) {
        this.objectMapper = objectMapper;
        this.secretName = secretName;
    }

    public ClientCredentials getClientCredentials() {
        ClientCredentials cached = cache.get();
        if (cached != null) return cached;

        if (secretName == null || secretName.isBlank()) {
            throw new IllegalStateException("Missing CLIENT_CREDENTIALS_SECRET_NAME (or secrets.client-credentials-name).");
        }

        // Create client on-demand to reduce init work and keep snapshot smaller.
        try (SecretsManagerClient sm = SecretsManagerClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(3))
                        .apiCallAttemptTimeout(Duration.ofSeconds(3))
                        .build())
                .build()) {

            String secretString = sm.getSecretValue(GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build()).secretString();

            if (secretString == null || secretString.isBlank()) {
                throw new IllegalStateException("Secret '" + secretName + "' has empty secretString.");
            }

            ClientCredentials creds = parseSecret(secretString);
            cache.compareAndSet(null, creds);
            log.info("Loaded client credentials from Secrets Manager. secretName={}", secretName);
            return Objects.requireNonNull(cache.get());
        }
    }

    private ClientCredentials parseSecret(String secretString) {
        try {
            JsonNode root = objectMapper.readTree(secretString);
            String clientId = text(root, "client_id");
            String clientSecret = text(root, "client_secret");

            if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
                throw new IllegalStateException("Secret must contain non-empty 'client_id' and 'client_secret'.");
            }
            return new ClientCredentials(clientId, clientSecret);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse client credentials from secret JSON.", e);
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n != null && !n.isNull() ? n.asText() : null;
    }
}
