package com.github.rzymek.opczip.excel;

import com.github.rzymek.opczip.CompressionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiThreadedExcelCompressor의 테스트 클래스입니다.
 */
public class MultiThreadedExcelCompressorTest {
    
    private MultiThreadedExcelCompressor compressor;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        compressor = new MultiThreadedExcelCompressor(2); // 2개 스레드 사용
    }
    
    @AfterEach
    void tearDown() {
        if (compressor != null) {
            compressor.close();
        }
    }
    
    @Test
    void testCompressEmptyWorkbook() {
        ExcelWorkbook workbook = new ExcelWorkbook();
        Path outputPath = tempDir.resolve("empty.xlsx");
        
        // 빈 워크북은 예외를 발생시켜야 함
        assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressWorkbook(workbook, outputPath);
        });
    }
    
    @Test
    void testCompressSingleSheetWorkbook() throws IOException {
        // 워크북 생성
        ExcelWorkbook workbook = new ExcelWorkbook();
        ExcelSheet sheet = workbook.createSheet("Sheet1");
        
        // 데이터 추가
        sheet.setCellValue("A1", "Hello");
        sheet.setCellValue("B1", "World");
        sheet.setCellValue("A2", 123);
        sheet.setCellValue("B2", 456.78);
        
        Path outputPath = tempDir.resolve("single_sheet.xlsx");
        
        // 압축 실행
        CompressionResult result = compressor.compressWorkbook(workbook, outputPath);
        
        // 결과 검증
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertTrue(result.getOriginalSize() > 0);
        assertTrue(result.getCompressedSize() > 0);
        assertTrue(result.getFileCount() > 0);
        assertNotNull(result.getCompressionTime());
        assertTrue(result.getCompressionTime().compareTo(Duration.ZERO) > 0);
        
        // 파일이 생성되었는지 확인
        assertTrue(java.nio.file.Files.exists(outputPath));
        assertTrue(java.nio.file.Files.size(outputPath) > 0);
        
        System.out.println("Single sheet compression result: " + result.getDetailedSummary());
    }
    
    @Test
    void testCompressMultipleSheetWorkbook() throws IOException {
        // 워크북 생성
        ExcelWorkbook workbook = new ExcelWorkbook();
        
        // 첫 번째 시트
        ExcelSheet sheet1 = workbook.createSheet("Sales Data");
        sheet1.setCellValue("A1", "Product");
        sheet1.setCellValue("B1", "Sales");
        sheet1.setCellValue("A2", "Product A");
        sheet1.setCellValue("B2", 1000);
        sheet1.setCellValue("A3", "Product B");
        sheet1.setCellValue("B3", 2000);
        
        // 두 번째 시트
        ExcelSheet sheet2 = workbook.createSheet("Customer Data");
        sheet2.setCellValue("A1", "Name");
        sheet2.setCellValue("B1", "Age");
        sheet2.setCellValue("A2", "John Doe");
        sheet2.setCellValue("B2", 30);
        sheet2.setCellValue("A3", "Jane Smith");
        sheet2.setCellValue("B3", 25);
        
        // 세 번째 시트
        ExcelSheet sheet3 = workbook.createSheet("Summary");
        sheet3.setCellValue("A1", "Total Sales");
        sheet3.setCellValue("B1", 3000);
        sheet3.setCellValue("A2", "Total Customers");
        sheet3.setCellValue("B2", 2);
        
        Path outputPath = tempDir.resolve("multiple_sheets.xlsx");
        
        // 압축 실행
        CompressionResult result = compressor.compressWorkbook(workbook, outputPath);
        
        // 결과 검증
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertTrue(result.getOriginalSize() > 0);
        assertTrue(result.getCompressedSize() > 0);
        assertTrue(result.getFileCount() > 0);
        assertNotNull(result.getCompressionTime());
        
        // 파일이 생성되었는지 확인
        assertTrue(java.nio.file.Files.exists(outputPath));
        assertTrue(java.nio.file.Files.size(outputPath) > 0);
        
        System.out.println("Multiple sheets compression result: " + result.getDetailedSummary());
    }
    
    @Test
    void testCompressLargeSheet() throws IOException {
        // 워크북 생성
        ExcelWorkbook workbook = new ExcelWorkbook();
        ExcelSheet sheet = workbook.createSheet("Large Data");
        
        // 대량 데이터 추가 (100x100 = 10,000 셀)
        for (int row = 0; row < 100; row++) {
            for (int col = 0; col < 100; col++) {
                if (col % 2 == 0) {
                    sheet.setCellValue(row, col, "Data_" + row + "_" + col);
                } else {
                    sheet.setCellValue(row, col, row * col);
                }
            }
        }
        
        Path outputPath = tempDir.resolve("large_sheet.xlsx");
        
        // 압축 실행
        long startTime = System.nanoTime();
        CompressionResult result = compressor.compressWorkbook(workbook, outputPath);
        long endTime = System.nanoTime();
        
        // 결과 검증
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertTrue(result.getOriginalSize() > 0);
        assertTrue(result.getCompressedSize() > 0);
        
        // 압축률 확인 (최소 10% 압축되어야 함)
        double compressionRatio = 1.0 - ((double) result.getCompressedSize() / result.getOriginalSize());
        assertTrue(compressionRatio > 0.1, "Compression ratio should be at least 10%");
        
        // 파일이 생성되었는지 확인
        assertTrue(java.nio.file.Files.exists(outputPath));
        assertTrue(java.nio.file.Files.size(outputPath) > 0);
        
        Duration totalTime = Duration.ofNanos(endTime - startTime);
        System.out.println("Large sheet compression result: " + result.getDetailedSummary());
        System.out.println("Total time (including test overhead): " + totalTime);
        System.out.println("Compression ratio: " + String.format("%.2f%%", compressionRatio * 100));
    }
    
    @Test
    void testCompressWithDifferentDataTypes() throws IOException {
        // 워크북 생성
        ExcelWorkbook workbook = new ExcelWorkbook();
        ExcelSheet sheet = workbook.createSheet("Data Types");
        
        // 다양한 데이터 타입 추가
        sheet.setCellValue("A1", "String Value");
        sheet.setCellValue("A2", 42);
        sheet.setCellValue("A3", 3.14159);
        sheet.setCellValue("A4", -100);
        sheet.setCellValue("A5", 0);
        sheet.setCellValue("A6", "");
        sheet.setCellValue("A7", "Special chars: <>&\"'");
        sheet.setCellValue("A8", 999999999L);
        sheet.setCellValue("A9", 0.000001);
        sheet.setCellValue("A10", "한글 텍스트");
        
        Path outputPath = tempDir.resolve("data_types.xlsx");
        
        // 압축 실행
        CompressionResult result = compressor.compressWorkbook(workbook, outputPath);
        
        // 결과 검증
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertTrue(result.getOriginalSize() > 0);
        assertTrue(result.getCompressedSize() > 0);
        
        // 파일이 생성되었는지 확인
        assertTrue(java.nio.file.Files.exists(outputPath));
        assertTrue(java.nio.file.Files.size(outputPath) > 0);
        
        System.out.println("Data types compression result: " + result.getDetailedSummary());
    }
    
    @Test
    void testCompressorClose() throws IOException {
        // 새로운 압축기 생성
        MultiThreadedExcelCompressor testCompressor = new MultiThreadedExcelCompressor(1);
        
        // 워크북 생성 및 압축
        ExcelWorkbook workbook = new ExcelWorkbook();
        ExcelSheet sheet = workbook.createSheet("Test");
        sheet.setCellValue("A1", "Test");
        
        Path outputPath = tempDir.resolve("close_test.xlsx");
        CompressionResult result = testCompressor.compressWorkbook(workbook, outputPath);
        
        assertTrue(result.isSuccessful());
        
        // 압축기 닫기
        testCompressor.close();
        
        // 닫힌 후에는 사용할 수 없어야 함
        assertThrows(IllegalStateException.class, () -> {
            testCompressor.compressWorkbook(workbook, tempDir.resolve("should_fail.xlsx"));
        });
    }
    
    @Test
    void testNullInputs() {
        // null 워크북
        assertThrows(NullPointerException.class, () -> {
            compressor.compressWorkbook(null, tempDir.resolve("test.xlsx"));
        });
        
        // null 출력 경로
        ExcelWorkbook workbook = new ExcelWorkbook();
        workbook.createSheet("Test");
        
        assertThrows(NullPointerException.class, () -> {
            compressor.compressWorkbook(workbook, null);
        });
    }
}