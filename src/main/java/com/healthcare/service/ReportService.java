package com.healthcare.service;

import com.healthcare.model.Report;
import com.healthcare.model.User;
import com.healthcare.repository.ReportRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/* iText 5 Imports */
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;

import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {
	
	private static Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    
    

    public ReportService(ReportRepository reportRepository) {
		
		this.reportRepository = reportRepository;
	}

	public Report generateReport(
            com.healthcare.model.Document document,
            User user
    ) {

        Report report = new Report();

        report.setReportTitle(
                "Medical Analysis Report - "
                        + document.getOriginalFilename()
        );

        report.setReportType("DOCUMENT_ANALYSIS");

        report.setContent(buildReportContent(document));

        report.setSummary(
                extractSummary(document.getAnalysisResult())
        );

        report.setStatus("GENERATED");

        report.setDocument(document);

        report.setUser(user);

        return reportRepository.save(report);
    }

    public byte[] downloadReportAsPDF(Long reportId)
            throws Exception {

        Report report = getReport(reportId);

        return generatePDF(report);
    }

    public byte[] downloadReportAsDOCX(Long reportId)
            throws IOException {

        Report report = getReport(reportId);

        return generateDOCX(report);
    }

    private Report getReport(Long reportId) {

        return reportRepository.findById(reportId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Report not found: " + reportId
                        )
                );
    }

    /* =========================================================
       PDF GENERATION
       ========================================================= */

    private byte[] generatePDF(Report report)
            throws Exception {

        ByteArrayOutputStream baos =
                new ByteArrayOutputStream();

        com.itextpdf.text.Document pdfDoc =
                new com.itextpdf.text.Document(
                        PageSize.A4,
                        50,
                        50,
                        80,
                        50
                );

        PdfWriter writer =
                PdfWriter.getInstance(pdfDoc, baos);

        /* Header/Footer */

        writer.setPageEvent(new PdfPageEventHelper() {

            @Override
            public void onEndPage(
                    PdfWriter writer,
                    com.itextpdf.text.Document document
            ) {

                try {

                    PdfContentByte cb =
                            writer.getDirectContent();

                    /* Header Background */

                    cb.setColorFill(
                            new BaseColor(0, 105, 148)
                    );

                    cb.rectangle(
                            0,
                            document.top() + 10,
                            document.getPageSize().getWidth(),
                            35
                    );

                    cb.fill();

                    /* Header Text */

                    Font headerFont =
                            FontFactory.getFont(
                                    FontFactory.HELVETICA_BOLD,
                                    12,
                                    BaseColor.WHITE
                            );

                    ColumnText.showTextAligned(
                            cb,
                            Element.ALIGN_CENTER,
                            new Phrase(
                                    "HealthAI - Agentic Healthcare System",
                                    headerFont
                            ),
                            document.getPageSize().getWidth() / 2,
                            document.top() + 22,
                            0
                    );

                    /* Footer */

                    Font footerFont =
                            FontFactory.getFont(
                                    FontFactory.HELVETICA,
                                    8,
                                    BaseColor.GRAY
                            );

                    ColumnText.showTextAligned(
                            cb,
                            Element.ALIGN_CENTER,
                            new Phrase(
                                    "Confidential Medical Report | Page "
                                            + writer.getPageNumber()
                                            + " | Generated by HealthAI",
                                    footerFont
                            ),
                            document.getPageSize().getWidth() / 2,
                            20,
                            0
                    );

                    /* Footer Line */

                    cb.setColorStroke(
                            new BaseColor(0, 105, 148)
                    );

                    cb.setLineWidth(1);

                    cb.moveTo(50, 35);

                    cb.lineTo(
                            document.getPageSize().getWidth() - 50,
                            35
                    );

                    cb.stroke();

                } catch (Exception e) {

                    log.error("PDF page event error", e);
                }
            }
        });

        pdfDoc.open();

        /* Title */

        Font titleFont =
                FontFactory.getFont(
                        FontFactory.HELVETICA_BOLD,
                        20,
                        new BaseColor(0, 105, 148)
                );

        Paragraph title =
                new Paragraph(
                        report.getReportTitle(),
                        titleFont
                );

        title.setAlignment(Element.ALIGN_CENTER);

        title.setSpacingBefore(20);

        title.setSpacingAfter(10);

        pdfDoc.add(title);

        /* Date */

        Font dateFont =
                FontFactory.getFont(
                        FontFactory.HELVETICA,
                        10,
                        BaseColor.GRAY
                );

        Paragraph date =
                new Paragraph(
                        "Generated: "
                                + report.getGeneratedAt().format(
                                DateTimeFormatter.ofPattern(
                                        "MMM dd, yyyy HH:mm"
                                )
                        ),
                        dateFont
                );

        date.setAlignment(Element.ALIGN_CENTER);

        date.setSpacingAfter(20);

        pdfDoc.add(date);

        /* Separator */

        LineSeparator separator = new LineSeparator();

        separator.setLineColor(
                new BaseColor(0, 105, 148)
        );

        pdfDoc.add(new Chunk(separator));

        pdfDoc.add(Chunk.NEWLINE);

        /* Content */

        String content = report.getContent();

        String[] sections = content.split("\n\n");

        Font sectionHeaderFont =
                FontFactory.getFont(
                        FontFactory.HELVETICA_BOLD,
                        13,
                        new BaseColor(0, 105, 148)
                );

        Font bodyFont =
                FontFactory.getFont(
                        FontFactory.HELVETICA,
                        10,
                        BaseColor.DARK_GRAY
                );

        Font boldBodyFont =
                FontFactory.getFont(
                        FontFactory.HELVETICA_BOLD,
                        10,
                        BaseColor.DARK_GRAY
                );

        for (String section : sections) {

            if (section.startsWith("**")
                    && section.contains("**")) {

                String headerText =
                        section.replace("**", "").trim();

                if (headerText.startsWith("#")) {

                    headerText =
                            headerText.replace("#", "").trim();

                    PdfPTable headerTable =
                            new PdfPTable(1);

                    headerTable.setWidthPercentage(100);

                    PdfPCell cell =
                            new PdfPCell(
                                    new Phrase(
                                            headerText,
                                            sectionHeaderFont
                                    )
                            );

                    cell.setBackgroundColor(
                            new BaseColor(230, 245, 255)
                    );

                    cell.setPadding(8);

                    cell.setBorderColor(
                            new BaseColor(0, 105, 148)
                    );

                    headerTable.addCell(cell);

                    headerTable.setSpacingBefore(10);

                    headerTable.setSpacingAfter(5);

                    pdfDoc.add(headerTable);

                } else {

                    pdfDoc.add(
                            new Paragraph(
                                    headerText,
                                    boldBodyFont
                            )
                    );
                }

            } else if (!section.trim().isEmpty()) {

                String[] lines = section.split("\n");

                for (String line : lines) {

                    if (!line.trim().isEmpty()) {

                        Paragraph para =
                                new Paragraph(
                                        line.replace("**", "")
                                                .replace("*", "")
                                                .trim(),
                                        bodyFont
                                );

                        para.setSpacingAfter(3);

                        if (line.trim().startsWith("-")
                                || line.trim().startsWith("•")) {

                            para.setIndentationLeft(20);
                        }

                        pdfDoc.add(para);
                    }
                }

                pdfDoc.add(Chunk.NEWLINE);
            }
        }

        /* Disclaimer */

        PdfPTable disclaimer =
                new PdfPTable(1);

        disclaimer.setWidthPercentage(100);

        disclaimer.setSpacingBefore(20);

        Font disclaimerFont =
                FontFactory.getFont(
                        FontFactory.HELVETICA_OBLIQUE,
                        8,
                        BaseColor.GRAY
                );

        PdfPCell disclaimerCell =
                new PdfPCell(
                        new Phrase(
                                "DISCLAIMER: This report is generated by an AI system and is for informational purposes only. "
                                        + "It does not constitute medical advice, diagnosis, or treatment. "
                                        + "Always consult healthcare professionals.",
                                disclaimerFont
                        )
                );

        disclaimerCell.setBackgroundColor(
                new BaseColor(255, 250, 230)
        );

        disclaimerCell.setPadding(10);

        disclaimer.addCell(disclaimerCell);

        pdfDoc.add(disclaimer);

        pdfDoc.close();

        return baos.toByteArray();
    }

    /* =========================================================
       DOCX GENERATION
       ========================================================= */

    private byte[] generateDOCX(Report report)
            throws IOException {

        try (
                XWPFDocument docx =
                        new XWPFDocument();

                ByteArrayOutputStream baos =
                        new ByteArrayOutputStream()
        ) {

            XWPFParagraph titlePara =
                    docx.createParagraph();

            titlePara.setAlignment(
                    ParagraphAlignment.CENTER
            );

            XWPFRun titleRun =
                    titlePara.createRun();

            titleRun.setBold(true);

            titleRun.setFontSize(18);

            titleRun.setColor("006994");

            titleRun.setFontFamily("Calibri");

            titleRun.setText(
                    report.getReportTitle()
            );

            titleRun.addBreak();

            XWPFParagraph contentPara =
                    docx.createParagraph();

            XWPFRun contentRun =
                    contentPara.createRun();

            contentRun.setFontSize(11);

            contentRun.setFontFamily("Calibri");

            contentRun.setText(
                    report.getContent()
            );

            docx.write(baos);

            return baos.toByteArray();
        }
    }

    /* =========================================================
       CONTENT BUILDER
       ========================================================= */

    private String buildReportContent(
            com.healthcare.model.Document document
    ) {

        StringBuilder content =
                new StringBuilder();

        content.append("## DOCUMENT INFORMATION\n");

        content.append("File Name: ")
                .append(document.getOriginalFilename())
                .append("\n");

        content.append("File Type: ")
                .append(document.getFileType())
                .append("\n");

        content.append("Upload Date: ")
                .append(
                        document.getUploadedAt().format(
                                DateTimeFormatter.ofPattern(
                                        "MMM dd, yyyy HH:mm"
                                )
                        )
                )
                .append("\n\n");

        content.append("## MEDICAL ANALYSIS\n");

        content.append(
                document.getAnalysisResult() != null
                        ? document.getAnalysisResult()
                        : "No analysis available"
        );

        content.append("\n\n");

        content.append("## HEALTH RECOMMENDATIONS\n");

        content.append(
                document.getSuggestions() != null
                        ? document.getSuggestions()
                        : "No suggestions available"
        );

        return content.toString();
    }

    private String extractSummary(
            String analysisResult
    ) {

        if (analysisResult == null) {

            return "No analysis available";
        }

        return analysisResult.length() > 500
                ? analysisResult.substring(0, 500) + "..."
                : analysisResult;
    }

    public List<Report> getUserReports(User user) {

        return reportRepository
                .findByUserOrderByGeneratedAtDesc(user);
    }

    public List<Report> getDocumentReports(Long documentId) {

        return reportRepository
                .findByDocumentId(documentId);
    }
}