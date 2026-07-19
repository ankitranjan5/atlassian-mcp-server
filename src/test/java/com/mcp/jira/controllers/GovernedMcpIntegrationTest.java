package com.mcp.jira.controllers;

import com.governedmcp.starter.governedmcp.annotation.GovernedMcpTool;
import com.governedmcp.starter.governedmcp.annotation.GovernedUserContext;
import com.governedmcp.starter.governedmcp.aop.GovernedMcpAspect;
import com.governedmcp.starter.governedmcp.audit.AuditLogger;
import com.governedmcp.starter.governedmcp.discovery.SchemaGenerator;
import com.governedmcp.starter.governedmcp.security.UserRoleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
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
@Import({GovernedMcpAspect.class, GovernedMcpIntegrationTest.DummySecurityTool.class})
public class GovernedMcpIntegrationTest {

    // 1. Create a lightweight, fake tool just for testing the Aspect
    @TestComponent
    public static class DummySecurityTool {
        @GovernedMcpTool(name = "dummy_tool", allowedRoles = {"ROLE_JIRA_DEVELOPER"})
        public String executeDummy(String input, @GovernedUserContext String userId) {
            // Echo back exactly what the AOP framework injected
            return "Executed input: " + input + ", Injected User: " + userId;
        }
    }

    // 2. Autowire the fake tool instead of the real AtlassianService
    @Autowired
    private DummySecurityTool dummySecurityTool;

    @MockBean
    private AuditLogger auditLogger;

    @MockBean
    private UserRoleProvider userRoleProvider;

    @BeforeEach
    public void setup() {
        SecurityContextHolder.clearContext();
    }

    private void setMockSecurityContext(String username) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    public void test_UnauthenticatedUser_ShouldThrowAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> {
            dummySecurityTool.executeDummy("PROJ-123", "malicious_injected_user");
        });
    }

    @Test
    public void test_UnauthorizedRole_ShouldThrowAccessDenied() {
        String testUuid = "bob_junior";
        setMockSecurityContext(testUuid);

        when(userRoleProvider.getRolesForUser(testUuid)).thenReturn(Set.of("ROLE_GUEST"));

        assertThrows(AccessDeniedException.class, () -> {
            dummySecurityTool.executeDummy("PROJ-123", "malicious_injected_user");
        });
    }

    @Test
    public void test_AuthorizedUser_ShouldSucceedAndInjectCorrectUserId() {
        String testUuid = "alice_vp";
        setMockSecurityContext(testUuid);

        when(userRoleProvider.getRolesForUser(testUuid)).thenReturn(Set.of("ROLE_JIRA_DEVELOPER"));

        String dirtyInput = "CEO_USER_ID";

        // Call our fake tool
        String result = dummySecurityTool.executeDummy("PROJ-123", dirtyInput);

        // Verify the Aspect intercepted and forcefully injected the UUID
        assertNotNull(result);
        assertTrue(result.contains("Injected User: alice_vp"));
        assertFalse(result.contains("CEO_USER_ID"));
    }

    @Test
    public void testGovernedSchema_ShouldHideUserId() throws NoSuchMethodException {
        SchemaGenerator generator = new SchemaGenerator();

        // Grab the method signature from the real service to prove the generator works
        Method method = AtlassianService.class.getMethod("getIssue", String.class, String.class);

        String generatedJsonSchema = generator.generateInputSchema(method);

        assertTrue(generatedJsonSchema.contains("issueId"));
        assertFalse(generatedJsonSchema.contains("verifiedUserId"));
    }
}