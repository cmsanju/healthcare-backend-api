package com.healthcare.event;

import lombok.Getter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

// Event classes
public class AIEvents {
	
	private static Logger log = LoggerFactory.getLogger(AIEvents.class);

    @Getter
    public static class QueryProcessedEvent extends ApplicationEvent {
        private final String sessionId;
        private final String agentType;
        private final String query;
        private final LocalDateTime timestamp;

        public QueryProcessedEvent(Object source, String sessionId, String agentType, String query) {
            super(source);
            this.sessionId = sessionId;
            this.agentType = agentType;
            this.query = query;
            this.timestamp = LocalDateTime.now();
        }

		public String getSessionId() {
			// TODO Auto-generated method stub
			return sessionId;
		}

		public String getAgentType() {
			// TODO Auto-generated method stub
			return agentType;
		}

		public String getQuery() {
			return query;
		}
    }

    @Getter
    public static class DocumentAnalyzedEvent extends ApplicationEvent {
        private final Long documentId;
        private final String status;
        private final String userId;

        public DocumentAnalyzedEvent(Object source, Long documentId, String status, String userId) {
            super(source);
            this.documentId = documentId;
            this.status = status;
            this.userId = userId;
        }

		

		public String getStatus() {
			// TODO Auto-generated method stub
			return status;
		}

		public Long getDocumentId() {
			// TODO Auto-generated method stub
			return documentId;
		}

		public String getUserId() {
			// TODO Auto-generated method stub
			return userId;
		}
    }

    @Getter
    public static class ReportGeneratedEvent extends ApplicationEvent {
        private final Long reportId;
        private final String reportType;

        public ReportGeneratedEvent(Object source, Long reportId, String reportType) {
            super(source);
            this.reportId = reportId;
            this.reportType = reportType;
        }

		public Long getReportId() {
			// TODO Auto-generated method stub
			return reportId;
		}

		public String getReportType() {
			// TODO Auto-generated method stub
			return reportType;
		}
    }

    @Getter
    public static class EmergencyDetectedEvent extends ApplicationEvent {
        private final String sessionId;
        private final String query;

        public EmergencyDetectedEvent(Object source, String sessionId, String query) {
            super(source);
            this.sessionId = sessionId;
            this.query = query;
        }

		public String getSessionId() {
			// TODO Auto-generated method stub
			return sessionId;
		}

		public String getQuery() {
			// TODO Auto-generated method stub
			return query;
		}
    }
}

// Event Listener
@Component
//@Slf4j
class AIEventListener {
	private static Logger log = LoggerFactory.getLogger(AIEventListener.class);
    @Async
    @EventListener
    public void handleQueryProcessed(AIEvents.QueryProcessedEvent event) {
        log.info("[EVENT] Query Processed | Session: {} | Agent: {} | Time: {}",
                event.getSessionId(), event.getAgentType(), event.getTimestamp());
    }

    @Async
    @EventListener
    public void handleDocumentAnalyzed(AIEvents.DocumentAnalyzedEvent event) {
        log.info("[EVENT] Document Analyzed | ID: {} | Status: {} | User: {}",
                event.getDocumentId(), event.getStatus(), event.getUserId());
    }

    @Async
    @EventListener
    public void handleReportGenerated(AIEvents.ReportGeneratedEvent event) {
        log.info("[EVENT] Report Generated | ID: {} | Type: {}",
                event.getReportId(), event.getReportType());
    }

    @Async
    @EventListener
    public void handleEmergency(AIEvents.EmergencyDetectedEvent event) {
        log.warn("[EVENT] ⚠️ EMERGENCY DETECTED | Session: {} | Query: {}",
                event.getSessionId(), event.getQuery());
        // In production: trigger alerts, notify emergency contacts, etc.
    }
}
