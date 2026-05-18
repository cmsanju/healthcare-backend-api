package com.healthcare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.healthcare.agent.AgentOrchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
//@RequiredArgsConstructor
//@Slf4j
public class GeminiAIService {
	
	private static Logger log = LoggerFactory.getLogger(GeminiAIService.class);


    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public String generateContent(String prompt) {
        return generateContent(prompt, null);
    }

    public String generateContent(String prompt, List<Map<String, String>> conversationHistory) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");

            // Add system instruction
            ObjectNode systemNode = objectMapper.createObjectNode();
            systemNode.put("role", "user");
            ArrayNode systemParts = systemNode.putArray("parts");
            ObjectNode systemPart = systemParts.addObject();
            systemPart.put("text", buildSystemPrompt());
            contents.add(systemNode);

            // Add model acknowledgment
            ObjectNode modelAck = objectMapper.createObjectNode();
            modelAck.put("role", "model");
            ArrayNode ackParts = modelAck.putArray("parts");
            ackParts.addObject().put("text", "I understand. I am HealthAI, your intelligent healthcare assistant. I'm ready to help with medical queries, document analysis, and health recommendations.");
            contents.add(modelAck);

            // Add conversation history if provided
            if (conversationHistory != null) {
                for (Map<String, String> msg : conversationHistory) {
                    ObjectNode msgNode = objectMapper.createObjectNode();
                    msgNode.put("role", "USER".equals(msg.get("role")) ? "user" : "model");
                    ArrayNode msgParts = msgNode.putArray("parts");
                    msgParts.addObject().put("text", msg.get("content"));
                    contents.add(msgNode);
                }
            }

            // Add current prompt
            ObjectNode userNode = objectMapper.createObjectNode();
            userNode.put("role", "user");
            ArrayNode userParts = userNode.putArray("parts");
            userParts.addObject().put("text", prompt);
            contents.add(userNode);

            // Generation config
            ObjectNode generationConfig = requestBody.putObject("generationConfig");
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topK", 40);
            generationConfig.put("topP", 0.95);
            generationConfig.put("maxOutputTokens", 8192);

            // Safety settings
            ArrayNode safetySettings = requestBody.putArray("safetySettings");
            addSafetySetting(safetySettings, "HARM_CATEGORY_HARASSMENT", "BLOCK_MEDIUM_AND_ABOVE");
            addSafetySetting(safetySettings, "HARM_CATEGORY_HATE_SPEECH", "BLOCK_MEDIUM_AND_ABOVE");
            addSafetySetting(safetySettings, "HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_MEDIUM_AND_ABOVE");
            addSafetySetting(safetySettings, "HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_MEDIUM_AND_ABOVE");

            String url = geminiApiUrl + "?key=" + geminiApiKey;
            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("Gemini API error: {} - {}", response.code(), errorBody);
                    return "I apologize, but I'm experiencing technical difficulties. Please try again. Error: " + response.code();
                }

                String responseBody = response.body().string();
                JsonNode jsonResponse = objectMapper.readTree(responseBody);

                JsonNode candidates = jsonResponse.get("candidates");
                if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                    JsonNode content = candidates.get(0).get("content");
                    if (content != null) {
                        JsonNode parts = content.get("parts");
                        if (parts != null && parts.isArray() && parts.size() > 0) {
                            return parts.get(0).get("text").asText();
                        }
                    }
                }
                return "I couldn't generate a response. Please try again.";
            }
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            return "I'm sorry, I encountered an error: " + e.getMessage();
        }
    }

    public String analyzeDocument(String documentText, String documentType) {
        String prompt = String.format("""
            You are an expert medical AI analyst. Analyze the following %s document and provide:
            
            1. **DOCUMENT SUMMARY**: Brief overview of the document content
            2. **KEY FINDINGS**: Important medical findings, values, or observations
            3. **MEDICAL ANALYSIS**: Detailed analysis of medical data present
            4. **RISK ASSESSMENT**: Any risk factors or concerning values identified
            5. **RECOMMENDATIONS**: Specific actionable recommendations
            6. **FOLLOW-UP ACTIONS**: Suggested next steps or follow-up tests
            7. **DISCLAIMER**: Medical disclaimer
            
            Document Content:
            %s
            
            Please provide a comprehensive, structured analysis using proper medical terminology while keeping explanations clear for the patient.
            """, documentType, documentText);

        return generateContent(prompt);
    }

    public String generateHealthSuggestions(String analysisResult) {
        String prompt = String.format("""
            Based on the following medical analysis, provide personalized health suggestions:
            
            Analysis: %s
            
            Please provide:
            1. **LIFESTYLE MODIFICATIONS**: Diet, exercise, sleep recommendations
            2. **PREVENTIVE MEASURES**: Steps to prevent worsening conditions
            3. **MEDICATION GUIDANCE**: General guidance (NOT specific prescriptions)
            4. **MONITORING PLAN**: What to monitor and how often
            5. **EMERGENCY SIGNS**: Warning signs that require immediate medical attention
            6. **WELLNESS TIPS**: General wellness improvements
            
            Format the response in a clear, patient-friendly manner.
            """, analysisResult);

        return generateContent(prompt);
    }

    private String buildSystemPrompt() {
        return """
                You are HealthAI, an advanced multi-agent healthcare AI assistant powered by cutting-edge artificial intelligence.
                
                Your capabilities include:
                1. **Medical Query Handling**: Answer general health questions with accuracy and empathy
                2. **Document Analysis**: Analyze medical reports, lab results, prescriptions, and health records
                3. **Symptom Assessment**: Help users understand symptoms (NOT diagnose)
                4. **Health Recommendations**: Provide evidence-based lifestyle and wellness recommendations
                5. **Medical Knowledge**: Share information about medications, conditions, and treatments
                6. **Emergency Guidance**: Identify emergencies and provide immediate guidance
                
                Guidelines:
                - Always recommend consulting healthcare professionals for diagnoses
                - Provide accurate, evidence-based information
                - Be empathetic, clear, and professional
                - Include appropriate disclaimers for medical advice
                - For emergencies, immediately direct to emergency services
                - Maintain patient confidentiality and privacy
                - Support multiple languages when needed
                
                You operate as part of a multi-agent system with specialized agents for:
                - General Health Queries (GeneralHealthAgent)
                - Medical Document Analysis (DocumentAnalysisAgent)  
                - Diagnostic Support (DiagnosticSupportAgent)
                - Prescription Analysis (PrescriptionAgent)
                - Emergency Response (EmergencyAgent)
                """;
    }

    private void addSafetySetting(ArrayNode settings, String category, String threshold) {
        ObjectNode setting = settings.addObject();
        setting.put("category", category);
        setting.put("threshold", threshold);
    }
}
