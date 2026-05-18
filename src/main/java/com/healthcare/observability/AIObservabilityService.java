package com.healthcare.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.healthcare.agent.AgentOrchestrator;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class AIObservabilityService {
	
	private static Logger log = LoggerFactory.getLogger(AIObservabilityService.class);

    private final MeterRegistry meterRegistry;
    private final Counter queryCounter;
    private final Counter errorCounter;
    private final Timer responseTimer;
    private final Map<String, AtomicLong> agentCallCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> queryTimestamps = new ConcurrentHashMap<>();

    public AIObservabilityService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.queryCounter = Counter.builder("healthcare.ai.queries.total")
                .description("Total AI queries processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("healthcare.ai.errors.total")
                .description("Total AI errors")
                .register(meterRegistry);
        this.responseTimer = Timer.builder("healthcare.ai.response.time")
                .description("AI response time")
                .register(meterRegistry);
    }

    public void recordQuery(String agentType, String sessionId) {
        queryCounter.increment();
        agentCallCounts.computeIfAbsent(agentType, k -> new AtomicLong(0)).incrementAndGet();
        queryTimestamps.put(sessionId, System.currentTimeMillis());
        log.info("[OBSERVABILITY] Query recorded | Agent: {} | Session: {} | Time: {}",
                agentType, sessionId, LocalDateTime.now());
    }

    public void recordResponse(String sessionId, int responseLength) {
        Long startTime = queryTimestamps.remove(sessionId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            log.info("[OBSERVABILITY] Response | Session: {} | Duration: {}ms | Length: {} chars",
                    sessionId, duration, responseLength);
        }
    }

    public void recordError(String agentType, String errorMessage) {
        errorCounter.increment();
        log.error("[OBSERVABILITY] AI Error | Agent: {} | Error: {}", agentType, errorMessage);
    }

    public void recordDocumentAnalysis(Long documentId, String status) {
        Counter.builder("healthcare.document.analysis")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
        log.info("[OBSERVABILITY] Document Analysis | ID: {} | Status: {}", documentId, status);
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("total_queries", queryCounter.count());
        metrics.put("total_errors", errorCounter.count());
        metrics.put("agent_call_counts", agentCallCounts);
        metrics.put("active_sessions", queryTimestamps.size());
        metrics.put("timestamp", LocalDateTime.now().toString());
        return metrics;
    }
}
