package com.healthcare.controller;

import com.healthcare.agent.AgentOrchestrator;
import com.healthcare.event.AIEvents;
import com.healthcare.model.Document;
import com.healthcare.model.Report;
import com.healthcare.model.User;
import com.healthcare.repository.UserRepository;
import com.healthcare.service.DocumentService;
import com.healthcare.service.ReportService;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@AllArgsConstructor
@Slf4j
public class ReportController {

	private static Logger log = LoggerFactory.getLogger(ReportController.class);
	
    private final ReportService reportService;
    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    

    public ReportController(ReportService reportService, DocumentService documentService, UserRepository userRepository,
			ApplicationEventPublisher eventPublisher) {
		
		this.reportService = reportService;
		this.documentService = documentService;
		this.userRepository = userRepository;
		this.eventPublisher = eventPublisher;
	}

	@PostMapping("/generate/{documentId}")
    public ResponseEntity<?> generateReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long documentId) {
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Document document = documentService.getDocumentById(documentId);

            if (!"ANALYZED".equals(document.getStatus())) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Document analysis not complete. Please wait for analysis to finish."));
            }

            Report report = reportService.generateReport(document, user);
            eventPublisher.publishEvent(new AIEvents.ReportGeneratedEvent(this, report.getId(), report.getReportType()));

            return ResponseEntity.ok(Map.of(
                "id", report.getId(),
                "title", report.getReportTitle(),
                "status", report.getStatus(),
                "generatedAt", report.getGeneratedAt().toString()
            ));
        } catch (Exception e) {
            log.error("Report generation error", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Report>> getUserReports(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(reportService.getUserReports(user));
    }

    @GetMapping("/{id}/download/pdf")
    public ResponseEntity<byte[]> downloadPDF(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        try {
            byte[] pdf = reportService.downloadReportAsPDF(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "HealthReport_" + id + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (Exception e) {
            log.error("PDF download error", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{id}/download/docx")
    public ResponseEntity<byte[]> downloadDOCX(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        try {
            byte[] docx = reportService.downloadReportAsDOCX(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDispositionFormData("attachment", "HealthReport_" + id + ".docx");
            return ResponseEntity.ok().headers(headers).body(docx);
        } catch (Exception e) {
            log.error("DOCX download error", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<Report>> getDocumentReports(@PathVariable Long documentId) {
        return ResponseEntity.ok(reportService.getDocumentReports(documentId));
    }
}
