package com.mcp.jira;

import com.mcp.jira.controllers.AtlassianService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@SpringBootApplication
@EnableCaching
public class AtlassianApplication {

	public static void main(String[] args) {
		SpringApplication.run(AtlassianApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider JiraTools(AtlassianService jiraService){

		ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
				.toolObjects(jiraService)
				.build();

		return provider;
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		// This is a dummy decoder just to get the application context to load.
		// It points to a fake JWKS endpoint.
		// DO NOT USE THIS IN PRODUCTION.
		return NimbusJwtDecoder.withJwkSetUri("https://mock-server.com/.well-known/jwks.json").build();
	}
}
