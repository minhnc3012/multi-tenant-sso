package com.identityplatform.sdk.service;

import com.identityplatform.sdk.IdpProperties;
import com.identityplatform.sdk.model.IdpCreateUserRequest;
import com.identityplatform.sdk.model.IdpPage;
import com.identityplatform.sdk.model.IdpUserResponse;
import com.identityplatform.sdk.token.IdpTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sync users between an org app and the IDP.
 *
 * <p>Push (org → IDP):
 * <ol>
 *   <li>Call {@link #createUser} — IDP creates user, returns IDP user ID</li>
 *   <li>Save local record with {@code idpUserId} from response</li>
 *   <li>IDP failure → exception → local DB is NOT saved (atomic)</li>
 * </ol>
 *
 * <p>Pull (IDP → org):
 * <ol>
 *   <li>Call {@link #listAllUsers} — fetches all org users from IDP</li>
 *   <li>Upsert into local DB by {@code idpUserId}</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class IdpUserSyncService {

    private final IdpProperties props;
    private final IdpTokenProvider tokenProvider;
    private final WebClient webClient;
    private final IdpOrgResolver orgResolver;

    public IdpUserResponse createUser(IdpCreateUserRequest request) {
        UUID orgId = orgResolver.getOrgId();
        String url = props.resolvedManagementBaseUrl() + "/api/v1/users/provision/" + orgId;
        log.debug("[IdpSDK] Provisioning user {} in org {}", request.email(), orgId);
        try {
            return webClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(IdpUserResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("[IdpSDK] createUser failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IdpSyncException("Failed to provision user in IDP: " + e.getMessage(), e);
        }
    }

    public IdpUserResponse updateUser(UUID idpUserId, IdpCreateUserRequest request) {
        String url = props.resolvedManagementBaseUrl() + "/api/v1/users/" + idpUserId;
        log.debug("[IdpSDK] Updating IDP user {}", idpUserId);
        try {
            return webClient.put()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(IdpUserResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("[IdpSDK] updateUser failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IdpSyncException("Failed to update user in IDP: " + e.getMessage(), e);
        }
    }

    public void deactivateUser(UUID idpUserId) {
        String url = props.resolvedManagementBaseUrl() + "/api/v1/users/" + idpUserId;
        log.info("[IdpSDK] Deactivating IDP user {}", idpUserId);
        try {
            webClient.delete()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            log.error("[IdpSDK] deactivateUser failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IdpSyncException("Failed to deactivate user in IDP: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches all users for this org from IDP (pages through all results).
     */
    public List<IdpUserResponse> listAllUsers() {
        UUID orgId = orgResolver.getOrgId();
        List<IdpUserResponse> result = new ArrayList<>();
        int page = 0;
        boolean last = false;
        while (!last) {
            String url = props.resolvedManagementBaseUrl()
                    + "/api/v1/users/org/" + orgId
                    + "?page=" + page + "&size=100&sort=createdAt,asc";
            try {
                IdpPage<IdpUserResponse> pageResult = webClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<IdpPage<IdpUserResponse>>() {})
                        .block();
                if (pageResult == null || pageResult.content().isEmpty()) break;
                result.addAll(pageResult.content());
                last = pageResult.last();
                page++;
            } catch (WebClientResponseException e) {
                log.error("[IdpSDK] listAllUsers failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new IdpSyncException("Failed to list users from IDP: " + e.getMessage(), e);
            }
        }
        log.info("[IdpSDK] Listed {} users from IDP for org {}", result.size(), orgId);
        return result;
    }

    // ── Unchecked exception ────────────────────────────────────────────────────

    public static class IdpSyncException extends RuntimeException {
        public IdpSyncException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
