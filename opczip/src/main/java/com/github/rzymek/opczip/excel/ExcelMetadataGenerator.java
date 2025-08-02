package com.github.rzymek.opczip.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel 파일의 메타데이터 XML 파일들을 생성하는 클래스입니다.
 * Office Open XML 형식에 필요한 관계 파일, 콘텐츠 타입 등을 생성합니다.
 */
public class ExcelMetadataGenerator {
    
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    
    /**
     * [Content_Types].xml 파일을 생성합니다.
     * 
     * @param workbook Excel 워크북
     * @return Content Types XML 바이트 배열
     * @throws IOException XML 생성 중 오류 발생시
     */
    public static byte[] generateContentTypesXml(ExcelWorkbook workbook) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            writer.write(XML_HEADER);
            writer.write("\n");
            
            writer.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
            writer.write("\n");
            
            // Default content types
            writer.write("  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n");
            writer.write("  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n");
            
            // Override content types
            writer.write("  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n");
            
            // Worksheet content types
            List<ExcelSheet> sheets = workbook.getSheets();
            for (int i = 0; i < sheets.size(); i++) {
                writer.write("  <Override PartName=\"/xl/worksheets/sheet");
                writer.write(String.valueOf(i + 1));
                writer.write(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n");
            }
            
            // Styles content type
            writer.write("  <Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>\n");
            
            // Shared strings content type (if needed)
            if (hasStringValues(workbook)) {
                writer.write("  <Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>\n");
            }
            
            writer.write("</Types>");
        }
        
        return baos.toByteArray();
    }
    
    /**
     * _rels/.rels 파일을 생성합니다.
     * 
     * @return Main relationships XML 바이트 배열
     * @throws IOException XML 생성 중 오류 발생시
     */
    public static byte[] generateMainRelsXml() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            writer.write(XML_HEADER);
            writer.write("\n");
            
            writer.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            writer.write("\n");
            
            writer.write("  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n");
            
            writer.write("</Relationships>");
        }
        
        return baos.toByteArray();
    }
    
    /**
     * xl/_rels/workbook.xml.rels 파일을 생성합니다.
     * 
     * @param workbook Excel 워크북
     * @return Workbook relationships XML 바이트 배열
     * @throws IOException XML 생성 중 오류 발생시
     */
    public static byte[] generateWorkbookRelsXml(ExcelWorkbook workbook) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            writer.write(XML_HEADER);
            writer.write("\n");
            
            writer.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            writer.write("\n");
            
            int relationshipId = 1;
            
            // Worksheet relationships
            List<ExcelSheet> sheets = workbook.getSheets();
            for (int i = 0; i < sheets.size(); i++) {
                writer.write("  <Relationship Id=\"rId");
                writer.write(String.valueOf(relationshipId++));
                writer.write("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet");
                writer.write(String.valueOf(i + 1));
                writer.write(".xml\"/>\n");
            }
            
            // Styles relationship
            writer.write("  <Relationship Id=\"rId");
            writer.write(String.valueOf(relationshipId++));
            writer.write("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n");
            
            // Shared strings relationship (if needed)
            if (hasStringValues(workbook)) {
                writer.write("  <Relationship Id=\"rId");
                writer.write(String.valueOf(relationshipId));
                writer.write("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>\n");
            }
            
            writer.write("</Relationships>");
        }
        
        return baos.toByteArray();
    }
    
    /**
     * docProps/app.xml 파일을 생성합니다.
     * 
     * @param workbook Excel 워크북
     * @return App properties XML 바이트 배열
     * @throws IOException XML 생성 중 오류 발생시
     */
    public static byte[] generateAppPropertiesXml(ExcelWorkbook workbook) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            writer.write(XML_HEADER);
            writer.write("\n");
            
            writer.write("<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" ");
            writer.write("xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">");
            writer.write("\n");
            
            writer.write("  <Application>MultiThreadedExcelCompressor</Application>\n");
            writer.write("  <DocSecurity>0</DocSecurity>\n");
            writer.write("  <ScaleCrop>false</ScaleCrop>\n");
            writer.write("  <LinksUpToDate>false</LinksUpToDate>\n");
            writer.write("  <SharedDoc>false</SharedDoc>\n");
            writer.write("  <HyperlinksChanged>false</HyperlinksChanged>\n");
            writer.write("  <AppVersion>16.0300</AppVersion>\n");
            
            // Heading pairs and titles of parts
            writer.write("  <HeadingPairs>\n");
            writer.write("    <vt:vector size=\"2\" baseType=\"variant\">\n");
            writer.write("      <vt:variant>\n");
            writer.write("        <vt:lpstr>Worksheets</vt:lpstr>\n");
            writer.write("      </vt:variant>\n");
            writer.write("      <vt:variant>\n");
            writer.write("        <vt:i4>");
            writer.write(String.valueOf(workbook.getSheetCount()));
            writer.write("</vt:i4>\n");
            writer.write("      </vt:variant>\n");
            writer.write("    </vt:vector>\n");
            writer.write("  </HeadingPairs>\n");
            
            writer.write("  <TitlesOfParts>\n");
            writer.write("    <vt:vector size=\"");
            writer.write(String.valueOf(workbook.getSheetCount()));
            writer.write("\" baseType=\"lpstr\">\n");
            
            for (ExcelSheet sheet : workbook.getSheets()) {
                writer.write("      <vt:lpstr>");
                writer.write(escapeXml(sheet.getSheetName()));
                writer.write("</vt:lpstr>\n");
            }
            
            writer.write("    </vt:vector>\n");
            writer.write("  </TitlesOfParts>\n");
            
            writer.write("</Properties>");
        }
        
        return baos.toByteArray();
    }
    
    /**
     * docProps/core.xml 파일을 생성합니다.
     * 
     * @return Core properties XML 바이트 배열
     * @throws IOException XML 생성 중 오류 발생시
     */
    public static byte[] generateCorePropertiesXml() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            writer.write(XML_HEADER);
            writer.write("\n");
            
            writer.write("<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" ");
            writer.write("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ");
            writer.write("xmlns:dcterms=\"http://purl.org/dc/terms/\" ");
            writer.write("xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" ");
            writer.write("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
            writer.write("\n");
            
            String currentTime = java.time.Instant.now().toString();
            
            writer.write("  <dc:creator>MultiThreadedExcelCompressor</dc:creator>\n");
            writer.write("  <cp:lastModifiedBy>MultiThreadedExcelCompressor</cp:lastModifiedBy>\n");
            writer.write("  <dcterms:created xsi:type=\"dcterms:W3CDTF\">");
            writer.write(currentTime);
            writer.write("</dcterms:created>\n");
            writer.write("  <dcterms:modified xsi:type=\"dcterms:W3CDTF\">");
            writer.write(currentTime);
            writer.write("</dcterms:modified>\n");
            
            writer.write("</cp:coreProperties>");
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 워크북에 문자열 값이 있는지 확인합니다.
     * 
     * @param workbook Excel 워크북
     * @return 문자열 값이 있으면 true
     */
    private static boolean hasStringValues(ExcelWorkbook workbook) {
        for (ExcelSheet sheet : workbook.getSheets()) {
            for (Object value : sheet.getCells().values()) {
                if (value instanceof String) {
                    return true;
                }
            }
        }
        return false;
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