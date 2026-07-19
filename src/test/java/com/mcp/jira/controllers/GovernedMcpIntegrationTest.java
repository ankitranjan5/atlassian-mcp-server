package com.mcp.jira.controllers;


import com.mcp.jira.controllers.AtlassianService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.governedmcp.starter.governedmcp.aop.GovernedMcpAspect;
import com.governedmcp.starter.governedmcp.discovery.SchemaGenerator;
import com.governedmcp.starter.governedmcp.audit.AuditLogger;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(GovernedMcpAspect.class)
public class GovernedMcpIntegrationTest {

    @Autowired
    private AtlassianService jiraToolService;

    @MockBean
    private AuditLogger auditLogger;

    private void setMockSecurityContext(String username, List<String> roles) {
        Jwt jwt = new Jwt("mock-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", username, "roles", roles));

        var authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    public void test_UnauthenticatedUser_ShouldThrowAccessDenied() {
        SecurityContextHolder.clearContext();

        assertThrows(AccessDeniedException.class, () -> {
            jiraToolService.getIssue("PROJ-123", "malicious_injected_user");
        });
    }

    @Test
    public void test_UnauthorizedRole_ShouldThrowAccessDenied() {
        // User has a valid token, but belongs to the wrong group/role
        setMockSecurityContext("bob_junior", Collections.singletonList("ROLE_GUEST"));

        assertThrows(AccessDeniedException.class, () -> {
            jiraToolService.getIssue("PROJ-123", "malicious_injected_user");
        });
    }

    @Test
    public void test_AuthorizedUser_ShouldSucceedAndInjectCorrectUserId() {
        // Alice has the correct role
        setMockSecurityContext("alice_vp", Collections.singletonList("ROLE_JIRA_DEVELOPER"));

        // We intentionally pass a dirty string to simulate prompt injection trying to impersonate the CEO
        String dirtyInput = "CEO_USER_ID";

        String result = jiraToolService.getIssue("PROJ-123", dirtyInput);

        // Verify the execution completed successfully
        assertNotNull(result);
        // Verify that the dirty input was successfully overridden by the cryptographic token subject
        assertTrue(result.contains("assignee = 'alice_vp'"));
        assertFalse(result.contains("CEO_USER_ID"));
    }

    @Test
    public void testGovernedSchema_ShouldHideUserId() throws NoSuchMethodException {
        // 1. Initialize our custom generator
        SchemaGenerator generator = new SchemaGenerator();

        // 2. Grab your exact method signature
        Method method = AtlassianService.class.getMethod("getIssue", String.class, String.class);

        // 3. Run it through the Phase 1 engine
        String generatedJsonSchema = generator.generateInputSchema(method);

        System.out.println("Generated MCP Schema:\n" + generatedJsonSchema);

        // 4. Verify the LLM can see the issueId
        assertTrue(generatedJsonSchema.contains("issueId"),
                "Schema should expose the issueId to the LLM");

        // 5. SECURITY CHECK: Verify the LLM cannot see the verifiedUserId
        assertFalse(generatedJsonSchema.contains("verifiedUserId"),
                "CRITICAL: Schema leaked the governed context parameter!");
    }
}
