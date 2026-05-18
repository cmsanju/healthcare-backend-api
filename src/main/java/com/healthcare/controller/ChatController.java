package com.healthcare.controller;

import com.healthcare.agent.AgentOrchestrator;
import com.healthcare.event.AIEvents;
import com.healthcare.model.User;
import com.healthcare.observability.AIObservabilityService;
import com.healthcare.repository.ChatMessageRepository;
import com.healthcare.repository.UserRepository;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
//@AllArgsConstructor
@Slf4j
public class ChatController {
	
	@Autowired
	private  AgentOrchestrator agentOrchestrator;
	@Autowired
    private  UserRepository userRepository;
	@Autowired
    private  ChatMessageRepository chatMessageRepository;
	@Autowired
    private  AIObservabilityService observabilityService;
	@Autowired
    private  ApplicationEventPublisher eventPublisher;
    
    /*
    public ChatController(AgentOrchestrator agentOrchestrator, UserRepository userRepository,
			ChatMessageRepository chatMessageRepository, AIObservabilityService observabilityService,
			ApplicationEventPublisher eventPublisher) {
		
		this.agentOrchestrator = agentOrchestrator;
		this.userRepository = userRepository;
		this.chatMessageRepository = chatMessageRepository;
		this.observabilityService = observabilityService;
		this.eventPublisher = eventPublisher;
	}
*/
	@PostMapping("/message")
    public ResponseEntity<?> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> request) {

        String query = request.get("message");
        String sessionId = request.getOrDefault("sessionId", UUID.randomUUID().toString());

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Record observability
        observabilityService.recordQuery("ORCHESTRATOR", sessionId);

        // Process with multi-agent system
        AgentOrchestrator.AgentResponse response = agentOrchestrator.processQuery(sessionId, query, user);

        // Record response metrics
        observabilityService.recordResponse(sessionId, response.content().length());

        // Publish event
        eventPublisher.publishEvent(new AIEvents.QueryProcessedEvent(this, sessionId, response.agentType(), query));

        // Check for emergency
        if ("EMERGENCY".equals(response.agentType())) {
            eventPublisher.publishEvent(new AIEvents.EmergencyDetectedEvent(this, sessionId, query));
        }

        return ResponseEntity.ok(Map.of(
            "message", response.content(),
            "agentType", response.agentType(),
            "sessionId", sessionId,
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<?> getChatHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String sessionId) {
        var messages = chatMessageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/clear/{sessionId}")
    public ResponseEntity<?> clearSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String sessionId) {
        chatMessageRepository.deleteBySessionId(sessionId);
        return ResponseEntity.ok(Map.of("message", "Session cleared successfully"));
    }

    @PostMapping("/new-session")
    public ResponseEntity<?> createSession(@AuthenticationPrincipal UserDetails userDetails) {
        String sessionId = UUID.randomUUID().toString();
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }
}
