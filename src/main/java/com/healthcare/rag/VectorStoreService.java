package com.healthcare.rag;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.healthcare.agent.AgentOrchestrator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval Augmented Generation) Service
 * Simulates a vector database for medical knowledge retrieval
 * In production, replace with Pinecone, Weaviate, or ChromaDB
 */
@Service
@Slf4j
public class VectorStoreService {
	
	private static Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    // Simulated in-memory vector store (represents embedded medical documents)
    private final Map<String, VectorDocument> vectorStore = new ConcurrentHashMap<>();

    public VectorStoreService() {
        initializeMedicalKnowledgeBase();
    }

    public void addDocument(String id, String content, Map<String, Object> metadata) {
        double[] embedding = generateSimpleEmbedding(content);
        VectorDocument doc = new VectorDocument(id, content, embedding, metadata);
        vectorStore.put(id, doc);
        log.debug("Document added to vector store: {}", id);
    }

    public List<VectorDocument> similaritySearch(String query, int topK) {
        double[] queryEmbedding = generateSimpleEmbedding(query);

        return vectorStore.values().stream()
                .map(doc -> {
                    double score = cosineSimilarity(queryEmbedding, doc.getEmbedding());
                    doc.setSimilarityScore(score);
                    return doc;
                })
                .sorted(Comparator.comparingDouble(VectorDocument::getSimilarityScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    public String retrieveRelevantContext(String query) {
        List<VectorDocument> relevant = similaritySearch(query, 3);
        if (relevant.isEmpty()) return "";

        StringBuilder context = new StringBuilder("Relevant medical knowledge:\n\n");
        for (VectorDocument doc : relevant) {
            if (doc.getSimilarityScore() > 0.1) {
                context.append("- ").append(doc.getContent()).append("\n\n");
            }
        }
        return context.toString();
    }

    private double[] generateSimpleEmbedding(String text) {
        // Simplified TF-IDF-like embedding (in production use sentence-transformers)
        String[] words = text.toLowerCase().split("\\s+");
        double[] embedding = new double[100];

        for (String word : words) {
            int hash = Math.abs(word.hashCode()) % 100;
            embedding[hash] += 1.0;
        }

        // Normalize
        double norm = 0;
        for (double v : embedding) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) embedding[i] /= norm;
        }
        return embedding;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void initializeMedicalKnowledgeBase() {
        // Seed medical knowledge base
        Map<String, String> medicalKnowledge = Map.of(
            "diabetes", "Diabetes mellitus is a chronic metabolic disease characterized by elevated blood glucose levels. Type 1 requires insulin therapy, Type 2 can be managed with lifestyle changes and medication. HbA1c target < 7% for most adults. Monitor blood glucose regularly.",
            "hypertension", "Hypertension (high blood pressure) is defined as systolic BP ≥130 mmHg or diastolic BP ≥80 mmHg. Lifestyle modifications: reduce sodium (<2.3g/day), exercise 150min/week, DASH diet, limit alcohol. First-line medications: ACE inhibitors, ARBs, thiazide diuretics, CCBs.",
            "cholesterol", "Optimal LDL cholesterol: <100 mg/dL for most adults, <70 mg/dL for high-risk patients. HDL cholesterol: >40 mg/dL (men), >50 mg/dL (women). Total cholesterol: <200 mg/dL desirable. Statins are first-line therapy for hypercholesterolemia.",
            "anemia", "Anemia defined as hemoglobin <13 g/dL (men) or <12 g/dL (women). Iron deficiency anemia: most common type, treat with iron supplementation. B12/folate deficiency: treat with supplementation. Screen for underlying causes.",
            "thyroid", "Hypothyroidism: TSH >4.5 mIU/L, treat with levothyroxine. Hyperthyroidism: TSH <0.5 mIU/L, may require antithyroid drugs or radioiodine therapy. Symptoms vary widely - fatigue, weight changes, temperature intolerance.",
            "covid", "COVID-19: caused by SARS-CoV-2. Symptoms include fever, cough, shortness of breath, loss of taste/smell. Vaccination is primary prevention. Treatment includes supportive care; antivirals for high-risk patients.",
            "heart", "Cardiovascular disease risk factors: hypertension, diabetes, hyperlipidemia, smoking, obesity, family history. Prevention: Mediterranean diet, regular exercise, smoking cessation, weight management. Warning signs: chest pain, shortness of breath, palpitations.",
            "kidney", "Chronic kidney disease (CKD) defined by GFR <60 for >3 months. Causes: diabetes, hypertension, glomerulonephritis. Management: control underlying causes, ACE inhibitors/ARBs, dietary protein restriction. Monitor creatinine, BUN, electrolytes.",
            "liver", "Liver function tests: ALT, AST, ALP, GGT, bilirubin, albumin, PT. Elevated liver enzymes may indicate hepatitis, fatty liver, cirrhosis. NAFLD is increasingly common. Avoid hepatotoxic medications. Hepatitis B and C require antiviral therapy.",
            "bone", "Osteoporosis: T-score ≤ -2.5 on DEXA scan. Risk factors: age, female sex, low BMI, smoking, corticosteroid use. Prevention: adequate calcium (1000-1200 mg/day) and vitamin D (600-800 IU/day), weight-bearing exercise."
        );

        medicalKnowledge.forEach((key, value) -> {
            addDocument("medical_" + key, value, Map.of("category", "medical_knowledge", "topic", key));
        });

        log.info("Medical knowledge base initialized with {} documents", vectorStore.size());
    }

    // Inner class for vector documents
    public static class VectorDocument {
        private String id;
        private String content;
        private double[] embedding;
        private Map<String, Object> metadata;
        private double similarityScore;

        public VectorDocument(String id, String content, double[] embedding, Map<String, Object> metadata) {
            this.id = id;
            this.content = content;
            this.embedding = embedding;
            this.metadata = metadata;
        }

        public String getId() { return id; }
        public String getContent() { return content; }
        public double[] getEmbedding() { return embedding; }
        public Map<String, Object> getMetadata() { return metadata; }
        public double getSimilarityScore() { return similarityScore; }
        public void setSimilarityScore(double score) { this.similarityScore = score; }
    }
}
