package com.healthcare.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.agent.AgentOrchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Model Context Protocol (MCP) Service
 * Manages tool definitions and execution for AI agents
 * MCP allows AI to use structured tools with defined schemas
 */
@Service
//@RequiredArgsConstructor
@Slf4j
public class MCPService {
	
	private static Logger log = LoggerFactory.getLogger(MCPService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Tool registry
    private final Map<String, MCPTool> toolRegistry = new LinkedHashMap<>();

    public MCPService() {
        registerHealthcareTools();
    }

    public void registerHealthcareTools() {
        // Medical Record Lookup Tool
        registerTool(new MCPTool(
            "lookup_medical_records",
            "Retrieve patient medical records and history",
            Map.of(
                "patient_id", "string",
                "record_type", "string"
            )
        ));

        // Drug Interaction Checker Tool
        registerTool(new MCPTool(
            "check_drug_interactions",
            "Check for potential drug interactions between medications",
            Map.of(
                "drug1", "string",
                "drug2", "string"
            )
        ));

        // Lab Result Analyzer Tool
        registerTool(new MCPTool(
            "analyze_lab_results",
            "Analyze laboratory test results and provide interpretation",
            Map.of(
                "test_name", "string",
                "value", "number",
                "unit", "string"
            )
        ));

        // Symptom Checker Tool
        registerTool(new MCPTool(
            "check_symptoms",
            "Analyze symptoms and suggest possible conditions",
            Map.of(
                "symptoms", "array",
                "duration", "string",
                "severity", "string"
            )
        ));

        // Appointment Scheduler Tool
        registerTool(new MCPTool(
            "schedule_appointment",
            "Schedule medical appointments",
            Map.of(
                "specialty", "string",
                "urgency", "string",
                "date_preference", "string"
            )
        ));

        log.info("MCP Tools registered: {}", toolRegistry.keySet());
    }

    public void registerTool(MCPTool tool) {
        toolRegistry.put(tool.getName(), tool);
    }

    public Map<String, Object> executeTool(String toolName, Map<String, Object> parameters) {
        log.info("MCP Tool execution: {} with params: {}", toolName, parameters);

        return switch (toolName) {
            case "analyze_lab_results" -> analyzeLabResults(parameters);
            case "check_drug_interactions" -> checkDrugInteractions(parameters);
            case "check_symptoms" -> checkSymptoms(parameters);
            case "lookup_medical_records" -> lookupMedicalRecords(parameters);
            default -> Map.of("status", "error", "message", "Tool not found: " + toolName);
        };
    }

    private Map<String, Object> analyzeLabResults(Map<String, Object> params) {
        String testName = (String) params.getOrDefault("test_name", "Unknown");
        Object valueObj = params.get("value");
        double value = valueObj instanceof Number ? ((Number) valueObj).doubleValue() : 0.0;
        String unit = (String) params.getOrDefault("unit", "");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("test", testName);
        result.put("value", value + " " + unit);
        result.put("interpretation", interpretLabValue(testName, value));
        result.put("normal_range", getNormalRange(testName));
        result.put("status", isValueNormal(testName, value) ? "NORMAL" : "ABNORMAL");
        return result;
    }

    private Map<String, Object> checkDrugInteractions(Map<String, Object> params) {
        String drug1 = (String) params.getOrDefault("drug1", "");
        String drug2 = (String) params.getOrDefault("drug2", "");

        return Map.of(
            "drug1", drug1,
            "drug2", drug2,
            "interaction_level", "MODERATE",
            "description", "Consult your pharmacist for specific interaction details between " + drug1 + " and " + drug2,
            "recommendation", "Always inform your doctor of all medications you are taking"
        );
    }

    private Map<String, Object> checkSymptoms(Map<String, Object> params) {
        Object symptomsObj = params.get("symptoms");
        String duration = (String) params.getOrDefault("duration", "");
        String severity = (String) params.getOrDefault("severity", "moderate");

        return Map.of(
            "symptoms_analyzed", symptomsObj,
            "duration", duration,
            "severity", severity,
            "recommendation", "Based on your symptoms, please consult a healthcare professional for proper evaluation",
            "urgency", "HIGH".equals(severity) ? "IMMEDIATE" : "ROUTINE"
        );
    }

    private Map<String, Object> lookupMedicalRecords(Map<String, Object> params) {
        return Map.of(
            "status", "success",
            "message", "Medical records lookup requires patient authentication",
            "record_types_available", List.of("Lab Results", "Prescriptions", "Visit Notes", "Imaging Reports")
        );
    }

    private String interpretLabValue(String testName, double value) {
        return switch (testName.toLowerCase()) {
            case "glucose", "blood glucose" -> value < 70 ? "Low (Hypoglycemia)" : value > 140 ? "High (Hyperglycemia)" : "Normal";
            case "hemoglobin", "hgb" -> value < 12 ? "Low (Anemia possible)" : value > 17 ? "High (Polycythemia possible)" : "Normal";
            case "wbc" -> value < 4000 ? "Low (Leukopenia)" : value > 11000 ? "High (Leukocytosis)" : "Normal";
            case "creatinine" -> value > 1.2 ? "Elevated (Kidney function concern)" : "Normal";
            default -> "Please consult your healthcare provider for interpretation";
        };
    }

    private String getNormalRange(String testName) {
        return switch (testName.toLowerCase()) {
            case "glucose" -> "70-99 mg/dL (fasting)";
            case "hemoglobin" -> "Men: 13.5-17.5 g/dL, Women: 12.0-15.5 g/dL";
            case "wbc" -> "4,500-11,000 cells/mcL";
            case "creatinine" -> "0.7-1.2 mg/dL (men), 0.5-1.1 mg/dL (women)";
            default -> "Consult reference ranges";
        };
    }

    private boolean isValueNormal(String testName, double value) {
        return switch (testName.toLowerCase()) {
            case "glucose" -> value >= 70 && value <= 99;
            case "hemoglobin" -> value >= 12 && value <= 17.5;
            case "creatinine" -> value <= 1.2;
            default -> true;
        };
    }

    public List<MCPTool> getAllTools() {
        return new ArrayList<>(toolRegistry.values());
    }

    public record MCPTool(String name, String description, Map<String, String> parameters) {

		public String getName() {
			return name;
		}

		public String description() {
			return description;
		}

		public Map<String, String> parameters() {
			return parameters;
		}
    	
    }
}
