package com.github.rzymek.opczip.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Excel 파일의 XML 구성 요소를 생성하는 클래스입니다.
 * Office Open XML 형식에 맞는 XML 파일들을 생성합니다.
 */
public class ExcelXmlGenerator {
    
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    
    /**
     * 워크시트 XML을 생성합니다.
     * 
     * @param sheet Excel 시트
     * @return 워크시트 XML 바이트 배열
     * @throws IOException XML 생성 중 오류 발생시
     */
    public static byte[] generateWorksheetXml(ExcelSheet sheet) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            writer.write(XML_HEADER);
            writer.write("\n");
            
            writer.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ");
            writer.write("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
            writer.write("\n");
            
            // Sheet dimensions
            if (!sheet.isEmpty()) {
                int maxRow = sheet.getMaxRow();
                int maxCol = sheet.getMaxColumn();
                String topLeft = "A1";
                String bottomRight = columnIndexToLetter(maxCol - 1) + maxRow;
                
                writer.write("  <dimension ref=\"");
                writer.write(topLeft);
                writer.write(":");
                writer.write(bottomRight);
                writer.write("\"/>\n");
            }
            
            // Sheet views
            writer.write("  <sheetViews>\n");
            writer.write("    <sheetView tabSelected=\"1\" workbookViewId=\"0\"/>\n");
            writer.write("  </sheetViews>\n");
            
            // Sheet format properties
            writer.write("  <sheetFormatPr defaultRowHeight=\"15\"/>\n");
            
            // Sheet data
            if (!sheet.isEmpty()) {
                writer.write("  <sheetData>\n");
                writeSheetData(writer, sheet);
                writer.write("  </sheetData>\n");
            }
            
            writer.write("</worksheet>");
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 워크북 XML을 생성합니다.
     * 
     * @param workbook Excel 워크북
     * @return 워크북 XML 바이트 배열
     * @throws IOException XML 생성 중 오류 발생시
     */
    public static byte[] generateWorkbookXml(ExcelWorkbook workbook) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            writer.write(XML_HEADER);
            writer.write("\n");
            
            writer.write("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ");
            writer.write("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
            writer.write("\n");
            
            // File version
            writer.write("  <fileVersion appName=\"xl\" lastEdited=\"6\" lowestEdited=\"6\" rupBuild=\"14420\"/>\n");
            
            // Workbook properties
            writer.write("  <workbookPr defaultThemeVersion=\"164011\"/>\n");
            
            // Book views
            writer.write("  <bookViews>\n");
            writer.write("    <workbookView xWindow=\"0\" yWindow=\"0\" windowWidth=\"22260\" windowHeight=\"12645\"/>\n");
            writer.write("  </bookViews>\n");
            
            // Sheets
            writer.write("  <sheets>\n");
            List<ExcelSheet> sheets = workbook.getSheets();
            for (int i = 0; i < sheets.size(); i++) {
                ExcelSheet sheet = sheets.get(i);
                writer.write("    <sheet name=\"");
                writer.write(escapeXml(sheet.getSheetName()));
                writer.write("\" sheetId=\"");
                writer.write(String.valueOf(i + 1));
                writer.write("\" r:id=\"rId");
                writer.write(String.valueOf(i + 1));
                writer.write("\"/>\n");
            }
            writer.write("  </sheets>\n");
            
            // Calculation properties
            writer.write("  <calcPr calcId=\"171027\"/>\n");
            
            writer.write("</workbook>");
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 스타일 XML을 생성합니다.
     * 
     * @return 스타일 XML 바이트 배열
     * @throws IOException XML 생성 중 오류 발생시
     */
    public static byte[] generateStylesXml() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            writer.write(XML_HEADER);
            writer.write("\n");
            
            writer.write("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            writer.write("\n");
            
            // Number formats
            writer.write("  <numFmts count=\"0\"/>\n");
            
            // Fonts
            writer.write("  <fonts count=\"1\">\n");
            writer.write("    <font>\n");
            writer.write("      <sz val=\"11\"/>\n");
            writer.write("      <color theme=\"1\"/>\n");
            writer.write("      <name val=\"Calibri\"/>\n");
            writer.write("      <family val=\"2\"/>\n");
            writer.write("      <scheme val=\"minor\"/>\n");
            writer.write("    </font>\n");
            writer.write("  </fonts>\n");
            
            // Fills
            writer.write("  <fills count=\"2\">\n");
            writer.write("    <fill>\n");
            writer.write("      <patternFill patternType=\"none\"/>\n");
            writer.write("    </fill>\n");
            writer.write("    <fill>\n");
            writer.write("      <patternFill patternType=\"gray125\"/>\n");
            writer.write("    </fill>\n");
            writer.write("  </fills>\n");
            
            // Borders
            writer.write("  <borders count=\"1\">\n");
            writer.write("    <border>\n");
            writer.write("      <left/>\n");
            writer.write("      <right/>\n");
            writer.write("      <top/>\n");
            writer.write("      <bottom/>\n");
            writer.write("      <diagonal/>\n");
            writer.write("    </border>\n");
            writer.write("  </borders>\n");
            
            // Cell style formats
            writer.write("  <cellStyleXfs count=\"1\">\n");
            writer.write("    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>\n");
            writer.write("  </cellStyleXfs>\n");
            
            // Cell formats
            writer.write("  <cellXfs count=\"1\">\n");
            writer.write("    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>\n");
            writer.write("  </cellXfs>\n");
            
            // Cell styles
            writer.write("  <cellStyles count=\"1\">\n");
            writer.write("    <cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/>\n");
            writer.write("  </cellStyles>\n");
            
            writer.write("</styleSheet>");
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 공유 문자열 XML을 생성합니다.
     * 
     * @param workbook Excel 워크북
     * @return 공유 문자열 XML 바이트 배열
     * @throws IOException XML 생성 중 오류 발생시
     */
    public static byte[] generateSharedStringsXml(ExcelWorkbook workbook) throws IOException {
        // 모든 문자열 값을 수집
        Map<String, Integer> stringMap = collectUniqueStrings(workbook);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            writer.write(XML_HEADER);
            writer.write("\n");
            
            writer.write("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ");
            writer.write("count=\"");
            writer.write(String.valueOf(stringMap.size()));
            writer.write("\" uniqueCount=\"");
            writer.write(String.valueOf(stringMap.size()));
            writer.write("\">");
            writer.write("\n");
            
            for (String str : stringMap.keySet()) {
                writer.write("  <si>\n");
                writer.write("    <t>");
                writer.write(escapeXml(str));
                writer.write("</t>\n");
                writer.write("  </si>\n");
            }
            
            writer.write("</sst>");
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 시트 데이터를 XML로 작성합니다.
     * 
     * @param writer XML 작성기
     * @param sheet Excel 시트
     * @throws IOException 작성 중 오류 발생시
     */
    private static void writeSheetData(Writer writer, ExcelSheet sheet) throws IOException {
        Map<String, Object> cells = sheet.getCells();
        
        // 행별로 그룹화
        Map<Integer, Map<String, Object>> rowData = groupCellsByRow(cells);
        
        for (Map.Entry<Integer, Map<String, Object>> rowEntry : rowData.entrySet()) {
            int rowNum = rowEntry.getKey();
            Map<String, Object> rowCells = rowEntry.getValue();
            
            writer.write("    <row r=\"");
            writer.write(String.valueOf(rowNum));
            writer.write("\">\n");
            
            for (Map.Entry<String, Object> cellEntry : rowCells.entrySet()) {
                String cellRef = cellEntry.getKey();
                Object value = cellEntry.getValue();
                
                writer.write("      <c r=\"");
                writer.write(cellRef);
                writer.write("\"");
                
                if (value instanceof String) {
                    writer.write(" t=\"inlineStr\"");
                }
                
                writer.write(">\n");
                
                if (value instanceof String) {
                    writer.write("        <is><t>");
                    writer.write(escapeXml(value.toString()));
                    writer.write("</t></is>\n");
                } else if (value instanceof Number) {
                    writer.write("        <v>");
                    writer.write(value.toString());
                    writer.write("</v>\n");
                } else if (value != null) {
                    writer.write("        <is><t>");
                    writer.write(escapeXml(value.toString()));
                    writer.write("</t></is>\n");
                }
                
                writer.write("      </c>\n");
            }
            
            writer.write("    </row>\n");
        }
    }
    
    /**
     * 셀을 행별로 그룹화합니다.
     * 
     * @param cells 셀 데이터
     * @return 행별로 그룹화된 셀 데이터
     */
    private static Map<Integer, Map<String, Object>> groupCellsByRow(Map<String, Object> cells) {
        Map<Integer, Map<String, Object>> rowData = new java.util.TreeMap<>();
        
        for (Map.Entry<String, Object> entry : cells.entrySet()) {
            String cellRef = entry.getKey();
            Object value = entry.getValue();
            
            int rowNum = extractRowNumber(cellRef);
            
            rowData.computeIfAbsent(rowNum, k -> new java.util.TreeMap<>())
                   .put(cellRef, value);
        }
        
        return rowData;
    }
    
    /**
     * 워크북에서 고유한 문자열을 수집합니다.
     * 
     * @param workbook Excel 워크북
     * @return 문자열과 인덱스 맵
     */
    private static Map<String, Integer> collectUniqueStrings(ExcelWorkbook workbook) {
        Map<String, Integer> stringMap = new java.util.LinkedHashMap<>();
        int index = 0;
        
        for (ExcelSheet sheet : workbook.getSheets()) {
            for (Object value : sheet.getCells().values()) {
                if (value instanceof String) {
                    String str = (String) value;
                    if (!stringMap.containsKey(str)) {
                        stringMap.put(str, index++);
                    }
                }
            }
        }
        
        return stringMap;
    }
    
    /**
     * 셀 참조에서 행 번호를 추출합니다.
     * 
     * @param cellReference 셀 참조
     * @return 행 번호
     */
    private static int extractRowNumber(String cellReference) {
        StringBuilder rowStr = new StringBuilder();
        
        for (int i = 0; i < cellReference.length(); i++) {
            char c = cellReference.charAt(i);
            if (Character.isDigit(c)) {
                rowStr.append(c);
            }
        }
        
        return Integer.parseInt(rowStr.toString());
    }
    
    /**
     * 열 인덱스를 Excel 열 문자로 변환합니다.
     * 
     * @param columnIndex 열 인덱스 (0부터 시작)
     * @return 열 문자
     */
    private static String columnIndexToLetter(int columnIndex) {
        StringBuilder result = new StringBuilder();
        
        while (columnIndex >= 0) {
            result.insert(0, (char) ('A' + (columnIndex % 26)));
            columnIndex = columnIndex / 26 - 1;
        }
        
        return result.toString();
    }
    
    /**
     * XML에서 특수 문자를 이스케이프합니다.
     * 
     * @param text 이스케이프할 텍스트
     * @return 이스케이프된 텍스트
     */
    private static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }
}