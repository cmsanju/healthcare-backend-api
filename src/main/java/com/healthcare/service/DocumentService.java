package com.healthcare.service;

import com.healthcare.model.Document;
import com.healthcare.model.User;
import com.healthcare.rag.VectorStoreService;
import com.healthcare.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
//@RequiredArgsConstructor
@Slf4j
public class DocumentService {

	private static Logger log = LoggerFactory.getLogger(DocumentService.class);
	
	@Autowired
    private DocumentRepository documentRepository;
	
	@Autowired
    private GeminiAIService geminiAIService;
	
	@Autowired
    private VectorStoreService vectorStoreService;
    
    

	@Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public Document uploadDocument(MultipartFile file, User user) throws IOException {
        // Create upload directory
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + extension;
        Path filePath = uploadPath.resolve(uniqueFilename);

        // Save file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Create document record
        Document document = new Document();
        document.setFilename(uniqueFilename);
        document.setOriginalFilename(originalFilename);
        document.setFilePath(filePath.toString());
        document.setFileType(extension.toUpperCase());
        document.setFileSize(file.getSize());
        document.setStatus("UPLOADED");
        document.setUser(user);

        Document saved = documentRepository.save(document);
        log.info("Document uploaded: {} by user: {}", originalFilename, user.getUsername());
        return saved;
    }

    @Async
    public void analyzeDocumentAsync(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        try {
            document.setStatus("ANALYZING");
            documentRepository.save(document);

            // Extract text from document
            String extractedText = extractText(document);
            document.setExtractedText(extractedText);

            // Add to vector store for RAG
            vectorStoreService.addDocument(
                "doc_" + documentId,
                extractedText.substring(0, Math.min(extractedText.length(), 2000)),
                Map.of("documentId", documentId, "type", document.getFileType())
            );

            // Analyze with Gemini AI
            String analysis = geminiAIService.analyzeDocument(extractedText, document.getFileType());
            document.setAnalysisResult(analysis);

            // Generate suggestions
            String suggestions = geminiAIService.generateHealthSuggestions(analysis);
            document.setSuggestions(suggestions);

            document.setStatus("ANALYZED");
            document.setAnalyzedAt(LocalDateTime.now());
            documentRepository.save(document);

            log.info("Document analyzed successfully: {}", documentId);
        } catch (Exception e) {
            log.error("Error analyzing document: {}", documentId, e);
            document.setStatus("FAILED");
            document.setAnalysisResult("Analysis failed: " + e.getMessage());
            documentRepository.save(document);
        }
    }

    private String extractText(Document document) throws IOException {
        String fileType = document.getFileType().toLowerCase();
        File file = new File(document.getFilePath());

        if (!file.exists()) {
            return "Document file not found. Please re-upload.";
        }

        return switch (fileType) {
            case "pdf" -> extractFromPDF(file);
            case "txt" -> Files.readString(Paths.get(document.getFilePath()));
            case "docx", "doc" -> extractFromDocx(file);
            default -> "Unsupported file type: " + fileType + ". Supported: PDF, TXT, DOCX";
        };
    }

    private String extractFromPDF(File file) throws IOException {

        try (PDDocument doc = Loader.loadPDF(file)) {

            PDFTextStripper stripper = new PDFTextStripper();

            String text = stripper.getText(doc);

            return text.length() > 50000
                    ? text.substring(0, 50000) + "...[truncated]"
                    : text;
        }
    }

    private String extractFromDocx(File file) throws IOException {
        try {
            org.apache.poi.xwpf.usermodel.XWPFDocument docx =
                    new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.FileInputStream(file));
            org.apache.poi.xwpf.extractor.XWPFWordExtractor extractor =
                    new org.apache.poi.xwpf.extractor.XWPFWordExtractor(docx);
            return extractor.getText();
        } catch (Exception e) {
            log.warn("Failed to extract DOCX text: {}", e.getMessage());
            return "Could not extract text from DOCX file. Please convert to PDF or TXT.";
        }
    }

    public List<Document> getUserDocuments(User user) {
        return documentRepository.findByUserOrderByUploadedAtDesc(user);
    }

    public Document getDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "txt";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
