package com.mcp.jira.controllers;

import com.mcp.jira.managers.TokenManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.jira.modals.ConfluenceModals;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
//@RestController
public class AtlassianService {

    @Autowired
    TokenManager tokenManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Helper method to get the Cloud ID for the current user.
     * This avoids repeating logic in every tool.
     */
    private String getCloudId(String accessToken) throws JsonProcessingException {
        String accessibleJson = WebClient.create()
                .get()
                .uri("https://api.atlassian.com/oauth/token/accessible-resources")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode arr = objectMapper.readTree(accessibleJson);
        if (arr.isEmpty()) {
            throw new RuntimeException("No accessible JIRA resources found for this user.");
        }
        return arr.get(0).get("id").asText();
    }

    private String getUriInstance(String accessToken) throws JsonProcessingException {
        String accessibleJson = WebClient.create()
                .get()
                .uri("https://api.atlassian.com/oauth/token/accessible-resources")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode arr = objectMapper.readTree(accessibleJson);
        if (arr.isEmpty()) {
            throw new RuntimeException("No accessible JIRA resources found for this user.");
        }
        return arr.get(0).get("url").asText();
    }

    /**
     * Helper to get the current authenticated user's access token.
     */
    private String getAccessToken() {
        String principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        return tokenManager.getToken(principal);
    }

    // ==================================================================================
    // TOOL 1: GET ISSUE (Filtered Response)
    // ==================================================================================
    @Tool(description = "Get Jira issue details by issue ID (e.g., PROJ-123)")
    public String getIssue(String issueId) {
        try {
            String accessToken = getAccessToken();
            String cloudId = getCloudId(accessToken);

            String responseJson = WebClient.create()
                    .get()
                    .uri("https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/3/issue/" + issueId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // --- Filter Logic: Extract only what Claude needs ---
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode fields = root.path("fields");

            String summary = fields.path("summary").asText("No Summary");
            String status = fields.path("status").path("name").asText("Unknown");
            String description = fields.path("description").path("content").findPath("text").asText("No description");
            String assignee = fields.path("assignee").path("displayName").asText("Unassigned");
            String priority = fields.path("priority").path("name").asText("None");

            // Return a clean, markdown-formatted string
            return String.format("""
                **Issue:** %s
                **Summary:** %s
                **Status:** %s
                **Priority:** %s
                **Assignee:** %s
                **Description:** %s
                """, root.path("key").asText(), summary, status, priority, assignee, description);

        } catch (Exception e) {
            return "Error fetching issue: " + e.getMessage();
        }
    }

    // ==================================================================================
    // TOOL 2: CREATE ISSUE
    // ==================================================================================
    @Tool(description = "Create a new Jira issue. Requires project key, summary, and issue type (e.g., Task, Bug).")
    public String createIssue(String projectKey, String summary, String issueType, String description) {
        try {
            String accessToken = getAccessToken();
            String cloudId = getCloudId(accessToken);

            // Construct the JSON payload for JIRA
            Map<String, Object> fields = new HashMap<>();
            fields.put("project", Map.of("key", projectKey));
            fields.put("summary", summary);
            fields.put("issuetype", Map.of("name", issueType));


            Map<String, Object> payload = Map.of("fields", fields);

            String response = WebClient.create()
                    .post()
                    .uri("https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/2/issue")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return "Successfully created issue: " + root.path("key").asText() + " (ID: " + root.path("id").asText() + ")";

        } catch (Exception e) {
            return "Error creating issue: " + e.getMessage();
        }
    }

    // ==================================================================================
    // TOOL 3: UPDATE ISSUE
    // ==================================================================================
    @Tool(description = "Update an existing Jira issue summary.")
    public String updateIssueSummary(String issueKey, String newSummary) {
        try {
            String accessToken = getAccessToken();
            String cloudId = getCloudId(accessToken);

            // Construct update payload
            Map<String, Object> fields = Map.of("summary", newSummary);
            Map<String, Object> payload = Map.of("fields", fields);

            WebClient.create()
                    .put()
                    .uri("https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/3/issue/" + issueKey)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity() // PUT usually returns 204 No Content
                    .block();

            return "Successfully updated summary for issue: " + issueKey;

        } catch (Exception e) {
            return "Error updating issue: " + e.getMessage();
        }
    }

    @Tool(description = "Search Confluence pages using Confluence Query Language(CQL).")
//    @GetMapping("/get/cql")
    public List<ConfluenceModals.ConfluencePageSummary> searchConfluencePages(@RequestParam String cql) throws Exception{
        try {
            String accessToken = getAccessToken();
            String cloudId = getCloudId(accessToken);

            String responseJson = WebClient.create()
                    .get()
                    .uri("https://api.atlassian.com/ex/confluence/" +cloudId+ "/wiki/rest/api/content/search?cql=" + cql+"&expand=space")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<ConfluenceModals.ConfluencePageSummary> summaries = ConfluenceModals.cleanResponse(responseJson);

            return summaries;

        } catch (Exception e) {
            throw new Exception("Error searching Confluence pages: " + e.getMessage());
        }
    }

    @Tool(description = "Get Confluence page content by page ID.")
//    @GetMapping("/get/confluence")
    public String getConfluencePageContent(@RequestParam String pageId) throws Exception{
        try {
            String accessToken = getAccessToken();
            String cloudId = getCloudId(accessToken);

            String responseJson = WebClient.create()
                    .get()
                    .uri("https://api.atlassian.com/ex/confluence/" +cloudId+ "/wiki/api/v2/pages/" + pageId +"?body-format=storage")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("Status: " +
                                            clientResponse.statusCode() +
                                            ", body: " + body)))
                    .bodyToMono(String.class)
                    .block();
//            return responseJson;

            JsonNode root = objectMapper.readTree(responseJson);
            String rawHtmlBody = root.path("body").path("storage").path("value").asText();

            ConfluenceModals confluenceModals = new ConfluenceModals();

            return confluenceModals.getPageContentForSummary(rawHtmlBody);

        } catch (Exception e) {
            return "Error fetching Confluence page content: " + e.getMessage();
        }
    }

//    @GetMapping("/get/confluence")
    @Tool(description = "Create a new Confluence page. REQUIRES a numeric spaceId (not spaceKey). Content must be in HTML storage format.")
    public String createConfluencePage(
            @RequestParam String spaceId,
            @RequestParam String title,
            @RequestBody String content) {
        try {
            String accessToken = getAccessToken();
            String cloudId = getCloudId(accessToken);

            // 1. Construct the Endpoint (Using your verified URL pattern)
            String url = "https://api.atlassian.com/ex/confluence/" + cloudId + "/wiki/api/v2/pages";

            // 2. Construct the V2 JSON Body
            // V2 requires a specific structure: { "spaceId": "...", "title": "...", "body": { "representation": "storage", "value": "..." } }
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("representation", "storage");
            bodyMap.put("value", content); // Ensure this is valid HTML (e.g., <p>hello</p>)

            Map<String, Object> payload = new HashMap<>();
            payload.put("spaceId", spaceId);
            payload.put("status", "current");
            payload.put("title", title);
            payload.put("body", bodyMap);

            // 3. Send POST Request
            String responseJson = WebClient.create()
                    .post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("Status: " +
                                            clientResponse.statusCode() +
                                            ", body: " + body)))
                    .bodyToMono(String.class)
                    .block();

            // 4. Extract and Return the New Page Link
            JsonNode root = objectMapper.readTree(responseJson);
            String id = root.path("id").asText();
            String webUi = root.path("_links").path("webui").asText();
            String base = root.path("_links").path("base").asText();

            return "Page Created Successfully! ID: " + id + "\nLink: " + base + webUi;

        } catch (Exception e) {
            return "Error creating page: " + e.getMessage();
        }
    }


}