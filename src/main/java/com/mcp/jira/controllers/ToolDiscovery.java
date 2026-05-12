package com.mcp.jira.controllers;

import com.mcp.jira.modals.McpTool;
import com.mcp.jira.service.ToolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal")
public class ToolDiscovery {
    private final ToolService toolService;

        public ToolDiscovery(ToolService toolService) {
            this.toolService = toolService;
        }

        @GetMapping("/tools")
        public List<McpTool> getTools() {
            return toolService.getAvailableTools();
        }
}
