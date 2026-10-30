package com.studentorgs.portal.ai.client;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.studentorgs.portal.common.client.InternalRestClientFactory;
import com.studentorgs.portal.common.config.OrgPortalProperties;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.error.ServiceUnavailableException;

/** Reads committee members from task-service ({@code /internal/committees/{id}/members}). */
@Component
public class TaskDirectoryClient {

    public record CommitteeMember(Long userId, String displayName) {
    }

    private final RestClient restClient;

    public TaskDirectoryClient(InternalRestClientFactory factory, OrgPortalProperties properties) {
        this.restClient = factory.create(properties.services().taskUrl());
    }

    public List<CommitteeMember> committeeMembers(Long committeeId) {
        try {
            List<CommitteeMember> members = restClient.get().uri("/internal/committees/{id}/members", committeeId)
                    .retrieve().body(new ParameterizedTypeReference<List<CommitteeMember>>() { });
            return members == null ? List.of() : members;
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Committee " + committeeId + " not found");
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Task service unavailable", e);
        }
    }
}
