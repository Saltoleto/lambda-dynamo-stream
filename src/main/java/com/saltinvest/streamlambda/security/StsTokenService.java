package com.saltinvest.streamlambda.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Retrieves and caches an access token using the OAuth2 Client Credentials flow.
 *
 * The user referred to this endpoint as "STS". In practice, it is usually an OAuth2 token endpoint (e.g. Cognito / custom IdP).
 *
 * Env vars (recommended):
 * - STS_TOKEN_URL: required
 * - STS_SCOPE: optional
 */
@Service
public class StsTokenService {

    private static final Logger log = LoggerFactory.getLogger(StsTokenService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SecretsManagerCredentialsProvider credentialsProvider;

    private final String tokenUrl;
    private final String scope;

    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

    public StsTokenService(
            RestClient restClient,
            ObjectMapper objectMapper,
            SecretsManagerCredentialsProvider credentialsProvider,
            @Value("${sts.token-url:${STS_TOKEN_URL:}}") String tokenUrl,
            @Value("${sts.scope:${STS_SCOPE:}}") String scope
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.credentialsProvider = credentialsProvider;
        this.tokenUrl = tokenUrl;
        this.scope = scope;
    }

    public String getAccessToken() {
        CachedToken cached = tokenCache.get();
        if (cached != null && !cached.isExpired()) {
            return cached.accessToken();
        }

        if (tokenUrl == null || tokenUrl.isBlank()) {
            throw new IllegalStateException("Missing STS_TOKEN_URL (or sts.token-url).");
        }

        ClientCredentials creds = credentialsProvider.getClientCredentials();
        ClientCredentialsTokenResponse response = requestToken(creds);

        String accessToken = response.accessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Token endpoint returned empty access_token.");
        }

        // expire a bit earlier to avoid edge cases
        long expiresInSec = response.expiresIn() != null ? response.expiresIn() : 300L;
        Instant expiresAt = Instant.now().plusSeconds(Math.max(30, expiresInSec - 30));

        CachedToken newToken = new CachedToken(accessToken, expiresAt);
        tokenCache.set(newToken);
        return accessToken;
    }

    private ClientCredentialsTokenResponse requestToken(ClientCredentials creds) {
        log.info("Requesting client_credentials token from STS. tokenUrl={}", tokenUrl);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", creds.clientId());
        form.add("client_secret", creds.clientSecret());
        if (scope != null && !scope.isBlank()) form.add("scope", scope);

        try {
            String json = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                throw new IllegalStateException("Token endpoint returned empty response body.");
            }

            return objectMapper.readValue(json, ClientCredentialsTokenResponse.class);
        } catch (Exception e) {
            log.error("Failed to obtain token from STS.", e);
            throw new IllegalStateException("Failed to obtain token from STS.", e);
        }
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isExpired() {
            return expiresAt == null || Instant.now().isAfter(expiresAt);
        }
    }
}
