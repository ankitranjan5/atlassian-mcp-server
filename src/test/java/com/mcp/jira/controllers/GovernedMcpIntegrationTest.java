package com.mcp.jira.controllers;

import com.governedmcp.starter.governedmcp.aop.GovernedMcpAspect;
import com.governedmcp.starter.governedmcp.audit.AuditLogger;
import com.governedmcp.starter.governedmcp.discovery.SchemaGenerator;
import com.governedmcp.starter.governedmcp.security.UserRoleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(GovernedMcpAspect.class)
public class GovernedMcpIntegrationTest {

    @Autowired
    private AtlassianService jiraToolService;

    @MockBean
    private AuditLogger auditLogger;

    // Mock the new provider instead of the security context roles
    @MockBean
    private UserRoleProvider userRoleProvider;

    @BeforeEach
    public void setup() {
        SecurityContextHolder.clearContext();
    }

    private void setMockSecurityContext(String username) {
        // Mimic the AppTokenFilter: Just standard authentication with the UUID as the principal.
        // No roles are injected here anymore.
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    public void test_UnauthenticatedUser_ShouldThrowAccessDenied() {
        // Context is already cleared by @BeforeEach
        assertThrows(AccessDeniedException.class, () -> {
            jiraToolService.getIssue("PROJ-123", "malicious_injected_user");
        });
    }

    @Test
    public void test_UnauthorizedRole_ShouldThrowAccessDenied() {
        String testUuid = "bob_junior";
        setMockSecurityContext(testUuid);

        // Instruct the mock provider to return a non-developer role
        when(userRoleProvider.getRolesForUser(testUuid)).thenReturn(Set.of("ROLE_GUEST"));

        assertThrows(AccessDeniedException.class, () -> {
            jiraToolService.getIssue("PROJ-123", "malicious_injected_user");
        });
    }

    @Test
    public void test_AuthorizedUser_ShouldSucceedAndInjectCorrectUserId() {
        String testUuid = "alice_vp";
        setMockSecurityContext(testUuid);

        // Instruct the mock provider to return the required role
        when(userRoleProvider.getRolesForUser(testUuid)).thenReturn(Set.of("ROLE_JIRA_DEVELOPER"));

        // We intentionally pass a dirty string to simulate prompt injection trying to impersonate the CEO
        String dirtyInput = "CEO_USER_ID";

        String result = jiraToolService.getIssue("PROJ-123", dirtyInput);

        // Verify the execution completed successfully
        assertNotNull(result);
        // Verify that the dirty input was successfully overridden by the verified subject
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