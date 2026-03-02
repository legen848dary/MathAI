package com.insoftu.mathai.service;

import com.insoftu.mathai.model.WorksheetResponse;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
public class PdfService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(30, 64, 175));
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(100, 116, 139));
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(30, 64, 175));
    private static final Font INSTRUCTIONS_FONT = new Font(Font.HELVETICA, 10, Font.ITALIC, new Color(71, 85, 105));
    private static final Font QUESTION_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(15, 23, 42));
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(30, 41, 59));
    private static final Font HINT_FONT = new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(100, 116, 139));
    private static final Font ANSWER_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(30, 41, 59));

    public byte[] generatePdf(WorksheetResponse worksheet) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 60, 60);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // ── Header ──────────────────────────────────────────────────────────
            addHeader(document, worksheet);

            // ── Instructions ────────────────────────────────────────────────────
            addInstructions(document, worksheet.instructions());

            document.add(new Paragraph(" "));

            // ── Questions ───────────────────────────────────────────────────────
            Paragraph questionsHeader = new Paragraph("Questions", SECTION_FONT);
            questionsHeader.setSpacingAfter(8);
            document.add(questionsHeader);

            for (WorksheetResponse.Question q : worksheet.questions()) {
                addQuestion(document, q);
            }

            // ── Answer Key (new page) ────────────────────────────────────────────
            document.newPage();
            addAnswerKey(document, worksheet);

        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        } finally {
            document.close();
        }

        return baos.toByteArray();
    }

    private void addHeader(Document doc, WorksheetResponse ws) throws DocumentException {
        // Blue top banner table
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(30, 64, 175));
        cell.setPadding(14);
        cell.setBorder(Rectangle.NO_BORDER);

        Paragraph title = new Paragraph(ws.title(), TITLE_FONT);
        title.getFont().setColor(Color.WHITE);
        cell.addElement(title);

        Paragraph meta = new Paragraph(
                ws.grade() + "  •  " + ws.topic() + "  •  " + ws.difficulty() + " difficulty",
                new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(186, 230, 253))
        );
        meta.setSpacingBefore(4);
        cell.addElement(meta);

        banner.addCell(cell);
        doc.add(banner);
        doc.add(new Paragraph(" "));
    }

    private void addInstructions(Document doc, String instructions) throws DocumentException {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(239, 246, 255));
        cell.setBorderColor(new Color(147, 197, 253));
        cell.setBorderWidth(1f);
        cell.setPadding(10);

        Paragraph label = new Paragraph("Instructions", new Font(Font.HELVETICA, 10, Font.BOLD, new Color(30, 64, 175)));
        cell.addElement(label);
        Paragraph inst = new Paragraph(instructions, INSTRUCTIONS_FONT);
        inst.setSpacingBefore(3);
        cell.addElement(inst);

        box.addCell(cell);
        doc.add(box);
    }

    private void addQuestion(Document doc, WorksheetResponse.Question q) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{0.5f, 9.5f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(4);

        // Number bubble
        PdfPCell numCell = new PdfPCell(new Phrase(String.valueOf(q.number()),
                new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE)));
        numCell.setBackgroundColor(new Color(59, 130, 246));
        numCell.setBorder(Rectangle.NO_BORDER);
        numCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        numCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        numCell.setPadding(6);
        table.addCell(numCell);

        // Question text + hint
        PdfPCell qCell = new PdfPCell();
        qCell.setBorder(Rectangle.BOTTOM);
        qCell.setBorderColor(new Color(226, 232, 240));
        qCell.setPaddingLeft(10);
        qCell.setPaddingBottom(8);
        qCell.setPaddingTop(4);

        qCell.addElement(new Paragraph(q.text(), QUESTION_FONT));

        if (q.hint() != null && !q.hint().isBlank()) {
            Paragraph hint = new Paragraph("Hint: " + q.hint(), HINT_FONT);
            hint.setSpacingBefore(3);
            qCell.addElement(hint);
        }

        // Work space lines
        Paragraph space = new Paragraph("\n\n\n", BODY_FONT);
        qCell.addElement(space);

        table.addCell(qCell);
        doc.add(table);
    }

    private void addAnswerKey(Document doc, WorksheetResponse ws) throws DocumentException {
        // Header
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(20, 83, 45));
        cell.setPadding(12);
        cell.setBorder(Rectangle.NO_BORDER);
        Paragraph title = new Paragraph("Answer Key — " + ws.title(),
                new Font(Font.HELVETICA, 14, Font.BOLD, Color.WHITE));
        cell.addElement(title);
        banner.addCell(cell);
        doc.add(banner);
        doc.add(new Paragraph(" "));

        for (String answer : ws.answerKey()) {
            PdfPTable row = new PdfPTable(1);
            row.setWidthPercentage(100);
            row.setSpacingBefore(4);
            PdfPCell aCell = new PdfPCell();
            aCell.setBackgroundColor(new Color(240, 253, 244));
            aCell.setBorderColor(new Color(134, 239, 172));
            aCell.setBorderWidth(1f);
            aCell.setPadding(8);
            aCell.addElement(new Paragraph(answer, ANSWER_FONT));
            row.addCell(aCell);
            doc.add(row);
        }
    }
}

