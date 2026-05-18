package com.healthcare.controller;

import com.healthcare.model.Document;
import com.healthcare.model.User;
import com.healthcare.repository.UserRepository;
import com.healthcare.service.DocumentService;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@AllArgsConstructor
@Slf4j
public class DocumentController {

	private static Logger log = LoggerFactory.getLogger(DocumentController.class);

	
    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    public DocumentController(DocumentService documentService, UserRepository userRepository,
			ApplicationEventPublisher eventPublisher) {
		
		this.documentService = documentService;
		this.userRepository = userRepository;
		this.eventPublisher = eventPublisher;
	}

	@PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please select a file"));
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.toLowerCase().endsWith(".pdf") &&
                    !filename.toLowerCase().endsWith(".txt") &&
                    !filename.toLowerCase().endsWith(".docx") &&
                    !filename.toLowerCase().endsWith(".doc"))) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Unsupported file type. Please upload PDF, TXT, or DOCX files."));
            }

            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Document document = documentService.uploadDocument(file, user);
            
            // Trigger async analysis
            documentService.analyzeDocumentAsync(document.getId());

            return ResponseEntity.ok(Map.of(
                "id", document.getId(),
                "filename", document.getOriginalFilename(),
                "status", document.getStatus(),
                "message", "Document uploaded successfully. Analysis in progress..."
            ));
        } catch (Exception e) {
            log.error("Upload error", e);
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Document>> getUserDocuments(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(documentService.getUserDocuments(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        try {
            Document document = documentService.getDocumentById(id);
            return ResponseEntity.ok(document);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getDocumentStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        try {
            Document document = documentService.getDocumentById(id);
            return ResponseEntity.ok(Map.of(
                "id", document.getId(),
                "status", document.getStatus(),
                "hasAnalysis", document.getAnalysisResult() != null,
                "hasSuggestions", document.getSuggestions() != null
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
