package com.b2binventory.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Writes tabular report data as CSV (UTF-8 with BOM so Excel opens it correctly) or XLSX. */
@Service
public class ExportService {

    public byte[] csv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        appendCsvRow(sb, headers.stream().map(h -> (Object) h).toList());
        rows.forEach(r -> appendCsvRow(sb, r));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] xlsx(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(bold);
                sheet.setColumnWidth(i, Math.min(60, Math.max(12, headers.get(i).length() + 4)) * 256);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    Object v = values.get(c);
                    Cell cell = row.createCell(c);
                    if (v instanceof BigDecimal d) cell.setCellValue(d.doubleValue());
                    else if (v instanceof Number n) cell.setCellValue(n.doubleValue());
                    else if (v != null) cell.setCellValue(v.toString());
                }
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create spreadsheet", e);
        }
    }

    private static void appendCsvRow(StringBuilder sb, List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            Object v = values.get(i);
            String s = v == null ? "" : v instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : v.toString();
            // Neutralize spreadsheet formula injection.
            if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !(v instanceof Number)) s = "'" + s;
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                s = "\"" + s.replace("\"", "\"\"") + "\"";
            }
            sb.append(s);
        }
        sb.append("\r\n");
    }
}
