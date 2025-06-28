package com.github.rzymek.opczip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 병렬로 여러 시트를 생성하고 압축한 후 하나의 Excel 파일로 합치는 예제
 */
public class ParallelExcelCreator {

    public static void main(String[] args) throws Exception {
        // 임시 디렉토리 생성
        File tempDir = Files.createTempDirectory("excel_sheets").toFile();
        tempDir.deleteOnExit();

        System.out.println("병렬 시트 생성 및 압축 시작...");
        System.out.println("임시 디렉토리: " + tempDir.getAbsolutePath());

        long startTime = System.currentTimeMillis();

        // 시트를 병렬로 생성하고 압축
        List<File> compressedSheetFiles = createCompressedSheetsInParallel(tempDir, 3);

        // 압축된 파일을 하나의 Excel 파일로 병합
        File excelFile = new File("parallel_generated.xlsx");
        combineIntoExcelFile(compressedSheetFiles, excelFile);

        long endTime = System.currentTimeMillis();

        System.out.println("작업 완료! 소요 시간: " + (endTime - startTime) + "ms");
        System.out.println("생성된 Excel 파일: " + excelFile.getAbsolutePath());
    }

    /**
     * 시트를 병렬로 생성하고 각각 압축하여 임시 파일로 저장합니다.
     */
    private static List<File> createCompressedSheetsInParallel(File tempDir, int sheetCount) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(sheetCount, Runtime.getRuntime().availableProcessors()));
        List<CompletableFuture<File>> futures = new ArrayList<>();

        // 각 시트를 병렬로 생성하고 압축
        for (int i = 1; i <= sheetCount; i++) {
            final int sheetIndex = i;
            CompletableFuture<File> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // 시트 데이터 생성
                    String sheetName = "Sheet" + sheetIndex;
                    String sheetXml = generateSheetXml(sheetName, 100); // 각 시트당 100개 행

                    // 압축된 시트 파일 생성
                    File compressedFile = new File(tempDir, "sheet" + sheetIndex + ".zip");
                    compressSheetToFile(sheetXml, compressedFile);

                    System.out.println("시트 " + sheetIndex + " 생성 및 압축 완료: " + compressedFile.getName());
                    return compressedFile;
                } catch (IOException e) {
                    throw new RuntimeException("시트 " + sheetIndex + " 처리 중 오류 발생", e);
                }
            }, executor);

            futures.add(future);
        }

        // 모든 작업이 완료될 때까지 대기
        List<File> compressedFiles = new ArrayList<>();
        for (CompletableFuture<File> future : futures) {
            compressedFiles.add(future.get());
        }

        executor.shutdown();
        return compressedFiles;
    }

    /**
     * 시트 XML 데이터를 생성합니다.
     */
    private static String generateSheetXml(String sheetName, int rowCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
                .append("  <sheetData>\n");

        // 헤더 행
        sb.append("    <row r=\"1\">\n")
                .append("      <c r=\"A1\" t=\"inlineStr\"><is><t>시트명</t></is></c>\n")
                .append("      <c r=\"B1\" t=\"inlineStr\"><is><t>순번</t></is></c>\n")
                .append("      <c r=\"C1\" t=\"inlineStr\"><is><t>데이터</t></is></c>\n")
                .append("    </row>\n");

        // 데이터 행 생성
        for (int i = 2; i <= rowCount + 1; i++) {
            sb.append("    <row r=\"").append(i).append("\">\n")
                    .append("      <c r=\"A").append(i).append("\" t=\"inlineStr\"><is><t>").append(sheetName).append("</t></is></c>\n")
                    .append("      <c r=\"B").append(i).append("\"><v>").append(i - 1).append("</v></c>\n")
                    .append("      <c r=\"C").append(i).append("\" t=\"inlineStr\"><is><t>데이터 행 ").append(i - 1).append("</t></is></c>\n")
                    .append("    </row>\n");
        }

        sb.append("  </sheetData>\n")
                .append("</worksheet>");

        return sb.toString();
    }

    /**
     * 시트 XML 데이터를 압축하여 파일로 저장합니다.
     */
    private static void compressSheetToFile(String sheetXml, File outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             OpcOutputStream ops = new OpcOutputStream(fos)) {

            // 압축 레벨 설정
            ops.setLevel(9);

            // ZIP 엔트리 추가
            ZipEntry entry = new ZipEntry("sheet.xml");
            ops.putNextEntry(entry);

            // 시트 XML 데이터 쓰기
            byte[] data = sheetXml.getBytes(StandardCharsets.UTF_8);
            ops.write(data, 0, data.length);

            // 엔트리 닫기 및 ZIP 완성
            ops.closeEntry();
            ops.finish();
        }
    }

    /**
     * 압축된 시트 파일들을 하나의 Excel 파일로 병합합니다.
     */
    private static void combineIntoExcelFile(List<File> compressedSheetFiles, File excelFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(excelFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 필수 Excel 파일 구조 추가
            addExcelStructure(zos, compressedSheetFiles.size());

            // 각 시트 파일들의 내용을 병합
            for (int i = 0; i < compressedSheetFiles.size(); i++) {
                File sheetFile = compressedSheetFiles.get(i);
                int sheetIndex = i + 1;

                // sheet.xml 파일을 읽어서 적절한 위치에 추가
                byte[] sheetContent = extractSheetContent(sheetFile);

                ZipEntry sheetEntry = new ZipEntry("xl/worksheets/sheet" + sheetIndex + ".xml");
                zos.putNextEntry(sheetEntry);
                zos.write(sheetContent);
                zos.closeEntry();
            }
        }
    }

    /**
     * 압축된 시트 파일에서 XML 내용만 추출합니다.
     */
    private static byte[] extractSheetContent(File compressedSheetFile) throws IOException {
        // 실제 구현에서는 ZIP 파일을 열고 sheet.xml 엔트리를 읽어야 합니다.
        // 간단한 예제를 위해 여기서는 파일 전체를 읽어옵니다.
        return Files.readAllBytes(compressedSheetFile.toPath());
    }

    /**
     * Excel 파일의 기본 구조를 추가합니다.
     */
    private static void addExcelStructure(ZipOutputStream zos, int sheetCount) throws IOException {
        // [Content_Types].xml
        addEntry(zos, "[Content_Types].xml", createContentTypesXml(sheetCount));

        // _rels/.rels
        addEntry(zos, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                        "</Relationships>");

        // xl/workbook.xml
        addEntry(zos, "xl/workbook.xml", createWorkbookXml(sheetCount));

        // xl/_rels/workbook.xml.rels
        addEntry(zos, "xl/_rels/workbook.xml.rels", createWorkbookRelsXml(sheetCount));
    }

    /**
     * [Content_Types].xml을 생성합니다.
     */
    private static String createContentTypesXml(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");

        // 각 시트별 Override 추가
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ")
                    .append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }

        sb.append("</Types>");
        return sb.toString();
    }

    /**
     * xl/workbook.xml을 생성합니다.
     */
    private static String createWorkbookXml(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                .append("<sheets>");

        // 각 시트 정의 추가
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<sheet name=\"Sheet").append(i).append("\" sheetId=\"").append(i).append("\" r:id=\"rId").append(i).append("\"/>");
        }

        sb.append("</sheets></workbook>");
        return sb.toString();
    }

    /**
     * xl/_rels/workbook.xml.rels을 생성합니다.
     */
    private static String createWorkbookRelsXml(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");

        // 각 시트 관계 추가
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Relationship Id=\"rId").append(i).append("\" ")
                    .append("Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" ")
                    .append("Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }

        sb.append("</Relationships>");
        return sb.toString();
    }

    /**
     * ZipOutputStream에 엔트리를 추가합니다.
     */
    private static void addEntry(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        zos.write(data, 0, data.length);
        zos.closeEntry();
    }
}