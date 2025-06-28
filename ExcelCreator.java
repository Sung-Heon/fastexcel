import com.github.rzymek.opczip.OpcZipOutputStream;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;

public class ExcelCreator {

    public static void main(String[] args) throws IOException {
        try (FileOutputStream fos = new FileOutputStream("ThreeSheets.xlsx");
             OpcZipOutputStream zos = new OpcZipOutputStream(fos)) {

            addSheets(zos);

            addContentsType(zos);

            addRels(zos);

            addWorkbook(zos);

            addWorkbookrels(zos);


            System.out.println("Excel file with 3 sheets created successfully: ThreeSheets.xlsx");
        }
    }

    private static void addSheets(OpcZipOutputStream zos) throws IOException {
        // Add sheet1.xml
        addEntry(zos, "xl/worksheets/sheet1.xml", createEmptySheet("Sheet1"));

        // Add sheet2.xml
        addEntry(zos, "xl/worksheets/sheet2.xml", createEmptySheet("Sheet2"));

        // Add sheet3.xml
        addEntry(zos, "xl/worksheets/sheet3.xml", createEmptySheet("Sheet3"));
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

    private static String createEmptySheet(String sheetName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
               "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
               "<sheetData>" +
               // Add sample data for demonstration
               "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>Hello from " + sheetName + "</v></c></row>" +
               "</sheetData>" +
               "</worksheet>";
    }
    
    private static void addEntry(OpcZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        zos.write(data, 0, data.length);
        zos.closeEntry();
    }
}
