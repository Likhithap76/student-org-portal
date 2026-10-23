package com.studentorgs.portal.common.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.studentorgs.portal.common.error.ServiceUnavailableException;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;

/**
 * Reads user data owned by user-service through its {@code /internal/users/**} endpoints.
 * If user-service cannot be reached the call fails with {@link ServiceUnavailableException} (503);
 * callers never guess a user's status.
 */
public class UserDirectoryClient {

    static final int BATCH_SIZE = 100;
    private static final String UNAVAILABLE = "User service unavailable";

    private final RestClient restClient;

    public UserDirectoryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public record UserStatusInfo(UserStatus status, Role role) {
    }

    public record UserName(Long id, String displayName) {
    }

    /** Current status and role, or empty if the user does not exist. */
    public Optional<UserStatusInfo> status(Long userId) {
        try {
            return Optional.ofNullable(restClient.get().uri("/internal/users/{id}/status", userId)
                    .retrieve().body(UserStatusInfo.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ServiceUnavailableException(UNAVAILABLE, e);
        }
    }

    /** Display names by id. Unknown ids are simply missing from the map. */
    public Map<Long, String> names(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        List<Long> ids = new ArrayList<>(userIds.stream().distinct().toList());
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            List<Long> chunk = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
            try {
                List<UserName> found = restClient.get()
                        .uri(b -> b.path("/internal/users/batch").queryParam("ids", chunk.toArray()).build())
                        .retrieve().body(new ParameterizedTypeReference<List<UserName>>() { });
                if (found != null) {
                    found.forEach(u -> names.put(u.id(), u.displayName()));
                }
            } catch (RestClientException e) {
                throw new ServiceUnavailableException(UNAVAILABLE, e);
            }
        }
        return names;
    }

    /** Ids of all active users. */
    public List<Long> activeIds() {
        try {
            List<Long> ids = restClient.get().uri("/internal/users/active-ids")
                    .retrieve().body(new ParameterizedTypeReference<List<Long>>() { });
            return ids == null ? List.of() : ids;
        } catch (RestClientException e) {
            throw new ServiceUnavailableException(UNAVAILABLE, e);
        }
    }
}
