package com.company.attendance.service;

import com.opencsv.CSVWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ExportService {
    public void csv(OutputStream os, List<String> headers, List<List<Object>> rows) throws IOException {
        try (CSVWriter w = new CSVWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            w.writeNext(headers.toArray(String[]::new));
            for (List<Object> r : rows) w.writeNext(r.stream().map(o -> o == null ? "" : o.toString()).toArray(String[]::new));
        }
    }

    public void xlsx(OutputStream os, String sheetName, List<String> headers, List<List<Object>> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(sheetName);
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont(); f.setBold(true); bold.setFont(f);
            Row h = s.createRow(0);
            for (int i = 0; i < headers.size(); i++) { Cell c = h.createCell(i); c.setCellValue(headers.get(i)); c.setCellStyle(bold); }
            int n = 1;
            for (List<Object> r : rows) {
                Row row = s.createRow(n++);
                for (int i = 0; i < r.size(); i++) row.createCell(i).setCellValue(r.get(i) == null ? "" : r.get(i).toString());
            }
            for (int i = 0; i < headers.size(); i++) s.autoSizeColumn(i);
            wb.write(os);
        }
    }
}
