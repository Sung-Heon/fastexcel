import com.github.rzymek.opczip.OpcZipOutputStream;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;

public class ExcelCreator {

    public static void main(String[] args) throws IOException {
        List<List<Object>> sheet1Data = new ArrayList<>();
        sheet1Data.add(Arrays.asList("이름", "나이", "직업"));
        sheet1Data.add(Arrays.asList("김철수", 28, "개발자"));
        sheet1Data.add(Arrays.asList("이영희", 32, "디자이너"));
        sheet1Data.add(Arrays.asList("박민수", 45, "매니저"));

        List<List<Object>> sheet2Data = new ArrayList<>();
        sheet2Data.add(Arrays.asList("제품", "가격", "재고"));
        sheet2Data.add(Arrays.asList("노트북", 1500000, 10));
        sheet2Data.add(Arrays.asList("스마트폰", 800000, 25));
        sheet2Data.add(Arrays.asList("태블릿", 600000, 15));

        List<List<Object>> sheet3Data = new ArrayList<>();
        sheet3Data.add(Arrays.asList("날짜", "수입", "지출", "잔액"));
        sheet3Data.add(Arrays.asList("2025-01-01", 5000000, 2000000, 3000000));
        sheet3Data.add(Arrays.asList("2025-02-01", 6000000, 3000000, 3000000));
        sheet3Data.add(Arrays.asList("2025-03-01", 4500000, 2500000, 2000000));

        try (FileOutputStream fos = new FileOutputStream("ThreeSheets.xlsx");
             OpcZipOutputStream zos = new OpcZipOutputStream(fos)) {

            // 시트 추가 (데이터 포함)
            addSheets(zos, sheet1Data, sheet2Data, sheet3Data);

            addContentsType(zos);

            addRels(zos);

            addWorkbook(zos);

            addWorkbookrels(zos);

            System.out.println("Excel file with 3 sheets created successfully: ThreeSheets.xlsx");
        }
    }

    private static void addSheets(OpcZipOutputStream zos, List<List<Object>> sheet1Data,
                                  List<List<Object>> sheet2Data,
                                  List<List<Object>> sheet3Data) throws IOException {
        // Add sheet1.xml
        addEntry(zos, "xl/worksheets/sheet1.xml", createSheet("Sheet1", sheet1Data));

        // Add sheet2.xml
        addEntry(zos, "xl/worksheets/sheet2.xml", createSheet("Sheet2", sheet2Data));

        // Add sheet3.xml
        addEntry(zos, "xl/worksheets/sheet3.xml", createSheet("Sheet3", sheet3Data));
    }

    private static void addWorkbookrels(OpcZipOutputStream zos) throws IOException {
        // Add workbook.xml.rels
        addEntry(zos, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
                        "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/>" +
                        "</Relationships>");
    }

    private static void addWorkbook(OpcZipOutputStream zos) throws IOException {
        // Add workbook.xml
        addEntry(zos, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                        "<sheets>" +
                        "<sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/>" +
                        "<sheet name=\"Sheet2\" sheetId=\"2\" r:id=\"rId2\"/>" +
                        "<sheet name=\"Sheet3\" sheetId=\"3\" r:id=\"rId3\"/>" +
                        "</sheets>" +
                        "</workbook>");
    }

    private static void addRels(OpcZipOutputStream zos) throws IOException {
        // Add main .rels
        addEntry(zos, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                        "</Relationships>");
    }

    private static void addContentsType(OpcZipOutputStream zos) throws IOException {
        // Add content types
        addEntry(zos, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                        "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                        "<Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                        "</Types>");
    }

    /**
     * 데이터를 받아 Excel 시트 XML을 생성합니다.
     */
    private static String createSheet(String sheetName, List<List<Object>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetData>");

        for (int rowIndex = 0; rowIndex < data.size(); rowIndex++) {
            List<Object> rowData = data.get(rowIndex);
            int rowNum = rowIndex + 1;
            sb.append("<row r=\"").append(rowNum).append("\">");

            for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                Object cellValue = rowData.get(colIndex);
                String colLetter = columnToLetter(colIndex);
                String cellReference = colLetter + rowNum;

                String cellType = getCellType(cellValue);

                sb.append("<c r=\"").append(cellReference).append("\"");

                if ("s".equals(cellType)) {
                    sb.append(" t=\"inlineStr\"><is><t>").append(escapeXml(cellValue.toString())).append("</t></is></c>");
                } else if ("n".equals(cellType)) {
                    sb.append("><v>").append(cellValue).append("</v></c>");
                } else {
                    sb.append("><v></v></c>");
                }
            }

            sb.append("</row>");
        }

        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    /**
     * 셀 값의 타입을 판단하여 Excel XML의 타입 코드를 반환합니다.
     */
    private static String getCellType(Object value) {
        if (value == null) {
            return ""; // 비어있는 셀
        } else if (value instanceof String) {
            return "s"; // 문자열
        } else if (value instanceof Number) {
            return "n"; // 숫자
        } else {
            return "s"; // 기본은 문자열로 취급
        }
    }

    /**
     * 열 인덱스를 Excel 열 문자(A, B, C, ..., Z, AA, AB, ...)로 변환합니다.
     */
    private static String columnToLetter(int columnIndex) {
        StringBuilder result = new StringBuilder();
        while (columnIndex >= 0) {
            int remainder = columnIndex % 26;
            result.insert(0, (char) ('A' + remainder));
            columnIndex = (columnIndex / 26) - 1;
        }
        return result.toString();
    }

    /**
     * XML에서 사용할 수 없는 특수문자를 이스케이프 처리합니다.
     */
    private static String escapeXml(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static void addEntry(OpcZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        zos.write(data, 0, data.length);
        zos.closeEntry();
    }
}
