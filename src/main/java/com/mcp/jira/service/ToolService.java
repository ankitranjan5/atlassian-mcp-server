package com.mcp.jira.service;

import com.mcp.jira.modals.McpTool; // Assuming your domain record is here
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ToolService {

    private final List<McpTool> toolRegistry;

    public ToolService() {
        // We define the JSON Schema for every tool mapped exactly
        // to your AtlassianService @Tool method parameters.
        this.toolRegistry = List.of(

                // --- JIRA TOOLS ---
                new McpTool(
                        "getIssue",
                        "Get Jira issue details by issue ID (e.g., PROJ-123).",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "issueId", Map.of("type", "string", "description", "The Jira issue key")
                                ),
                                "required", List.of("issueId")
                        )
                ),
                new McpTool(
                        "searchJiraIssues",
                        "Search for Jira issues using JQL.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "jql", Map.of("type", "string", "description", "The Jira Query Language string")
                                ),
                                "required", List.of("jql")
                        )
                ),
                new McpTool(
                        "createIssue",
                        "Create a new Jira issue.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "projectKey", Map.of("type", "string"),
                                        "summary", Map.of("type", "string"),
                                        "issueType", Map.of("type", "string"),
                                        "description", Map.of("type", "string") // Optional in your code
                                ),
                                "required", List.of("projectKey", "summary", "issueType")
                        )
                ),
                new McpTool(
                        "updateIssueSummary",
                        "Update an existing Jira issue summary.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "issueKey", Map.of("type", "string"),
                                        "newSummary", Map.of("type", "string")
                                ),
                                "required", List.of("issueKey", "newSummary")
                        )
                ),

                // --- CONFLUENCE TOOLS ---
                new McpTool(
                        "searchConfluencePages",
                        "Search Confluence pages using CQL.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "cql", Map.of("type", "string", "description", "The Confluence Query Language string")
                                ),
                                "required", List.of("cql")
                        )
                ),
                new McpTool(
                        "getConfluencePageContent",
                        "Get Confluence page content by page ID.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "pageId", Map.of("type", "string")
                                ),
                                "required", List.of("pageId")
                        )
                ),
                new McpTool(
                        "getConfluenceSpaces",
                        "Lists all available Confluence Spaces.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(), // No parameters needed
                                "required", List.of()
                        )
                ),
                new McpTool(
                        "createConfluencePage",
                        "Create a new Confluence page.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "spaceId", Map.of("type", "string"),
                                        "title", Map.of("type", "string"),
                                        "content", Map.of("type", "string", "description", "The storage format content of the page")
                                ),
                                "required", List.of("spaceId", "title", "content")
                        )
                )
        );
    }

    public List<McpTool> getAvailableTools() {
        log.debug("Discovery request: returning {} registered tools", toolRegistry.size());
        return this.toolRegistry;
    }
}