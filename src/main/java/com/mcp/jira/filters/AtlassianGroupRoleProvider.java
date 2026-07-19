package com.mcp.jira.filters;

import com.governedmcp.starter.governedmcp.security.UserRoleProvider;
import com.mcp.jira.clients.AtlassianClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConfigurationProperties(prefix = "mcp.security")
public class AtlassianGroupRoleProvider implements UserRoleProvider {

    // Automatically populated from application.properties
    private Map<String, String> groupMapping;

    private final AtlassianClient atlassianClient;

    public AtlassianGroupRoleProvider(AtlassianClient atlassianClient) {
        this.atlassianClient = atlassianClient;
    }

    // Required for Spring Boot property binding
    public void setGroupMapping(Map<String, String> groupMapping) {
        this.groupMapping = groupMapping;
    }

    @Override
    @Cacheable(value = "user_roles", key = "#p0")
    public Set<String> getRolesForUser(String userUuid) {
        // 1. Fetch groups using your heavily optimized client
        List<String> userGroups = atlassianClient.getGroups();

        // Fallback if no mappings are defined in properties
        if (groupMapping == null || groupMapping.isEmpty()) {
            return Set.of();
        }

        // 2. Map Atlassian groups to MCP Roles safely handling nulls
        return userGroups.stream()
                .map(group -> groupMapping.get(group.toLowerCase()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}