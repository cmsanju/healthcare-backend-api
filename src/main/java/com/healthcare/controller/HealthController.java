package com.healthcare.controller;

import com.healthcare.mcp.MCPService;
import com.healthcare.observability.AIObservabilityService;
import com.healthcare.service.AIMemoryService;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
//@AllArgsConstructor
public class HealthController {

	
	@Autowired
    private  AIObservabilityService observabilityService;
	@Autowired
    private  AIMemoryService memoryService;
	@Autowired
    private  MCPService mcpService;
    
	/*
    public HealthController(AIObservabilityService observabilityService, AIMemoryService memoryService,
			MCPService mcpService) {
		
		this.observabilityService = observabilityService;
		this.memoryService = memoryService;
		this.mcpService = mcpService;
	}
*/
	@GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "HealthAI Backend",
            "version", "1.0.0",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics() {
        return ResponseEntity.ok(Map.of(
            "ai_metrics", observabilityService.getMetrics(),
            "active_sessions", memoryService.getActiveSessionCount(),
            "mcp_tools", mcpService.getAllTools().size()
        ));
    }

    @GetMapping("/mcp/tools")
    public ResponseEntity<?> getMCPTools() {
        List<MCPService.MCPTool> tools = mcpService.getAllTools();
        return ResponseEntity.ok(tools);
    }

    @PostMapping("/mcp/execute")
    public ResponseEntity<?> executeMCPTool(@RequestBody Map<String, Object> request) {
        String toolName = (String) request.get("tool");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("parameters");
        Map<String, Object> result = mcpService.executeTool(toolName, params);
        return ResponseEntity.ok(result);
    }
}
