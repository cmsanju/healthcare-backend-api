package com.healthcare.agent;

import com.healthcare.mcp.MCPService;
import com.healthcare.rag.VectorStoreService;
import com.healthcare.service.AIMemoryService;
import com.healthcare.service.GeminiAIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Multi-Agent Orchestrator
 * Routes queries to specialized agents based on intent detection
 * Implements LangGraph-style workflow with agent coordination
 */
@Service
//@RequiredArgsConstructor
@Slf4j
public class AgentOrchestrator {
	
	private static Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final GeminiAIService geminiAIService;
    private final AIMemoryService memoryService;
    private final VectorStoreService vectorStoreService;
    private final MCPService mcpService;
    
    public AgentOrchestrator(GeminiAIService geminiAIService, AIMemoryService memoryService,
			VectorStoreService vectorStoreService, MCPService mcpService) {
		
		this.geminiAIService = geminiAIService;
		this.memoryService = memoryService;
		this.vectorStoreService = vectorStoreService;
		this.mcpService = mcpService;
	}

	public enum AgentType {
        GENERAL_HEALTH,
        DIAGNOSTIC_SUPPORT,
        DOCUMENT_ANALYSIS,
        EMERGENCY,
        PRESCRIPTION,
        WELLNESS,
        NUTRITION
    }

    public AgentResponse processQuery(String sessionId, String userQuery, com.healthcare.model.User user) {
        log.info("Processing query for session: {} | Agent Orchestrator", sessionId);

        // Step 1: Detect intent and route to appropriate agent
        AgentType agentType = detectIntent(userQuery);
        log.info("Intent detected: {}", agentType);

        // Step 2: Retrieve conversation memory
        List<Map<String, String>> conversationHistory = memoryService.getMemory(sessionId);

        // Step 3: RAG - Retrieve relevant medical knowledge
        String ragContext = vectorStoreService.retrieveRelevantContext(userQuery);

        // Step 4: Build enriched prompt based on agent type
        String enrichedPrompt = buildAgentPrompt(agentType, userQuery, ragContext, conversationHistory);

        // Step 5: Execute query with Gemini AI
        String response = geminiAIService.generateContent(enrichedPrompt, conversationHistory);

        // Step 6: Save to memory
        memoryService.addToMemory(sessionId, "USER", userQuery, user);
        memoryService.addToMemory(sessionId, "ASSISTANT", response, user);

        return new AgentResponse(response, agentType.name(), sessionId);
    }

    private AgentType detectIntent(String query) {
        String lowerQuery = query.toLowerCase();

        // Emergency detection (highest priority)
        if (containsAny(lowerQuery, "emergency", "chest pain", "can't breathe", "heart attack",
                "stroke", "unconscious", "severe pain", "bleeding heavily", "overdose")) {
            return AgentType.EMERGENCY;
        }

        // Prescription/Medication
        if (containsAny(lowerQuery, "medication", "drug", "prescription", "dose", "tablet",
                "capsule", "mg", "medicine", "antibiotic", "side effect")) {
            return AgentType.PRESCRIPTION;
        }

        // Diagnostic
        if (containsAny(lowerQuery, "diagnose", "diagnosis", "symptom", "test result", "lab",
                "blood test", "scan", "mri", "xray", "report", "biopsy")) {
            return AgentType.DIAGNOSTIC_SUPPORT;
        }

        // Nutrition
        if (containsAny(lowerQuery, "diet", "nutrition", "food", "eat", "calorie", "vitamin",
                "mineral", "supplement", "weight", "bmi")) {
            return AgentType.NUTRITION;
        }

        // Wellness
        if (containsAny(lowerQuery, "exercise", "fitness", "sleep", "stress", "mental health",
                "yoga", "meditation", "lifestyle", "wellness")) {
            return AgentType.WELLNESS;
        }

        return AgentType.GENERAL_HEALTH;
    }

    private String buildAgentPrompt(AgentType agentType, String query, String ragContext,
                                     List<Map<String, String>> history) {
        String agentInstruction = switch (agentType) {
            case EMERGENCY -> """
                    ⚠️ EMERGENCY AGENT ACTIVATED ⚠️
                    This appears to be a medical emergency. 
                    1. IMMEDIATELY advise calling emergency services (112/911)
                    2. Provide basic first aid guidance if applicable
                    3. Stay calm and provide clear, simple instructions
                    4. Do NOT delay emergency response for detailed explanations
                    """;
            case DIAGNOSTIC_SUPPORT -> """
                    You are the Diagnostic Support Agent.
                    Analyze symptoms and test results carefully.
                    Provide differential diagnoses and recommend appropriate tests.
                    Always remind that official diagnosis requires a physician.
                    Use clinical reasoning and evidence-based medicine.
                    """;
            case PRESCRIPTION -> """
                    You are the Prescription Analysis Agent.
                    Provide detailed medication information including:
                    - Mechanism of action
                    - Common dosages (general, not personalized)
                    - Side effects and contraindications
                    - Drug interactions to watch for
                    Always advise consulting a pharmacist or doctor for personal medication decisions.
                    """;
            case NUTRITION -> """
                    You are the Nutrition & Diet Agent.
                    Provide science-based nutritional advice.
                    Consider medical conditions when giving dietary recommendations.
                    Include specific foods, portion sizes, and meal timing when relevant.
                    """;
            case WELLNESS -> """
                    You are the Wellness & Lifestyle Agent.
                    Focus on evidence-based wellness recommendations.
                    Address physical, mental, and emotional health holistically.
                    Provide actionable, practical advice for sustainable lifestyle changes.
                    """;
            default -> """
                    You are the General Health Agent.
                    Provide accurate, helpful health information.
                    Be empathetic and patient-centered in your responses.
                    """;
        };

        StringBuilder prompt = new StringBuilder();
        prompt.append(agentInstruction).append("\n\n");

        if (!ragContext.isEmpty()) {
            prompt.append("Medical Knowledge Base Context:\n").append(ragContext).append("\n\n");
        }

        prompt.append("User Query: ").append(query);

        return prompt.toString();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    public record AgentResponse(String content, String agentType, String sessionId) {}
}
