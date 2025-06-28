package com.github.rzymek.opczip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Excel 파일의 ZIP 구조를 분석하는 클래스
 */
public class ExcelAnalyzer {
    
    public static void main(String[] args) throws IOException {
        // 분석할 Excel 파일 경로
        String excelFilePath = "ThreeSheets.xlsx";
        if (args.length > 0) {
            excelFilePath = args[0];
        }
        
        File excelFile = new File(excelFilePath);
        if (!excelFile.exists()) {
            System.err.println("파일을 찾을 수 없습니다: " + excelFilePath);
            return;
        }
        
        System.out.println("Excel 파일 분석 시작: " + excelFilePath);
        System.out.println("파일 크기: " + excelFile.length() + " 바이트");
        System.out.println("-------------------------------------");
        
        // 전체 ZIP 파일 구조 확인
        System.out.println("1. 전체 바이트 구조 분석:");
        printHexDump(excelFilePath, 128); // 처음 128바이트만 출력
        System.out.println("...");
        
        // ZIP 엔트리들 분석
        analyzeZipEntries(excelFile);
        
        // Excel Open XML 형식 설명
        explainOpenXmlFormat();
    }
    
    /**
     * 파일의 바이트를 헥사 형식으로 출력합니다.
     */
    private static void printHexDump(String filePath, int maxBytes) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        int bytesToShow = Math.min(fileBytes.length, maxBytes);
        
        for (int i = 0; i < bytesToShow; i += 16) {
            // 주소 출력
            System.out.printf("%08X  ", i);
            
            // 헥사 값 출력
            for (int j = 0; j < 16; j++) {
                if (i + j < bytesToShow) {
                    System.out.printf("%02X ", fileBytes[i + j] & 0xFF);
                } else {
                    System.out.print("   ");
                }
            }
            
            // 구분자
            System.out.print(" | ");
            
            // ASCII 출력
            for (int j = 0; j < 16; j++) {
                if (i + j < bytesToShow) {
                    byte b = fileBytes[i + j];
                    // 출력 가능한 ASCII 문자만 표시
                    if (b >= 32 && b < 127) {
                        System.out.print((char) b);
                    } else {
                        System.out.print(".");
                    }
                }
            }
            System.out.println();
        }
    }
    
    /**
     * ZIP 파일의 각 엔트리를 분석합니다.
     */
    private static void analyzeZipEntries(File zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int entryCount = 0;
            
            System.out.println("\n2. ZIP 엔트리 목록:");
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                
                System.out.printf("\n엔트리 #%d: %s\n", entryCount, entry.getName());
                System.out.println("  압축된 크기: " + entry.getCompressedSize() + " 바이트");
                System.out.println("  압축 해제 크기: " + entry.getSize() + " 바이트");
                System.out.println("  CRC-32: " + Long.toHexString(entry.getCrc()));
                
                // XML 파일의 경우 내용 일부 출력
                if (entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels")) {
                    System.out.println("  파일 내용 미리보기:");
                    printEntryContent(zip, entry, 300);
                }
                
                // 엔트리의 바이트 구조 분석
                analyzeEntryStructure(entry.getName(), entryCount);
            }
            
            System.out.println("\n총 엔트리 수: " + entryCount);
        }
    }
    
    /**
     * ZIP 파일 내의 엔트리 내용을 출력합니다.
     */
    private static void printEntryContent(ZipFile zip, ZipEntry entry, int maxLength) throws IOException {
        try (InputStream is = zip.getInputStream(entry)) {
            byte[] buffer = new byte[maxLength];
            int bytesRead = is.read(buffer, 0, maxLength);
            
            if (bytesRead > 0) {
                String content = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                System.out.println("    " + content.replace("\n", "\n    ").substring(0, Math.min(bytesRead, 200)) + "...");
            } else {
                System.out.println("    (내용 없음)");
            }
        }
    }
    
    /**
     * 엔트리의 바이너리 구조를 분석합니다.
     */
    private static void analyzeEntryStructure(String entryName, int entryIndex) throws IOException {
        // 이 부분은 더 복잡하고 고급 분석이 필요합니다.
        // 현재는 간단한 설명만 제공합니다.
        System.out.println("  바이트 구조 설명:");
        System.out.println("    - 로컬 파일 헤더 (30 바이트): PK\\x03\\x04 시그니처로 시작");
        System.out.println("    - 파일 이름: " + entryName);
        System.out.println("    - 압축된 데이터");
        System.out.println("    - (선택적) 데이터 디스크립터: CRC, 압축 크기, 압축 해제 크기 포함");
    }

    /**
     * Excel Open XML 형식에 대한 설명을 출력합니다.
     */
    private static void explainOpenXmlFormat() {
        System.out.println("\n3. Excel Open XML 형식 설명:");
        System.out.println("  - [Content_Types].xml: 파일 유형 정의");
        System.out.println("  - _rels/.rels: 패키지 수준 관계 정의");
        System.out.println("  - xl/workbook.xml: 워크북 정보 및 시트 목록");
        System.out.println("  - xl/_rels/workbook.xml.rels: 워크북 관계 정의");
        System.out.println("  - xl/worksheets/sheet1.xml, sheet2.xml, ...: 각 워크시트 데이터");
        System.out.println("  - xl/sharedStrings.xml: 공유 문자열 테이블 (선택적)");
        System.out.println("  - xl/styles.xml: 스타일 정의 (선택적)");
        System.out.println("  - xl/theme/theme1.xml: 테마 정보 (선택적)");
    }
}
