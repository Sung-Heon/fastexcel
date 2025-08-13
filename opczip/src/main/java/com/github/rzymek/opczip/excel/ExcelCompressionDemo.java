package com.github.rzymek.opczip.excel;

import com.github.rzymek.opczip.CompressionResult;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * MultiThreadedExcelCompressor의 사용 예제를 보여주는 데모 클래스입니다.
 */
public class ExcelCompressionDemo {
    
    public static void main(String[] args) {
        System.out.println("=== Multi-Threaded Excel Compressor Demo ===\n");
        
        try {
            // 1. 간단한 Excel 파일 생성 데모
            demonstrateSimpleExcelCreation();

            // 2. 다중 시트 Excel 파일 생성 데모
            demonstrateMultiSheetExcelCreation();

            // 3. 대용량 Excel 파일 생성 데모
            demonstrateLargeExcelCreation();

            // 4. 성능 비교 데모
            demonstratePerformanceComparison();
            
        } catch (IOException e) {
            System.err.println("Error during demo: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 간단한 Excel 파일 생성을 시연합니다.
     */
    private static void demonstrateSimpleExcelCreation() throws IOException {
        System.out.println("1. Simple Excel File Creation Demo");
        System.out.println("==================================");
        
        // 워크북 생성
        ExcelWorkbook workbook = new ExcelWorkbook();
        ExcelSheet sheet = workbook.createSheet("Sample Data");
        
        // 헤더 추가
        sheet.setCellValue("A1", "Name");
        sheet.setCellValue("B1", "Age");
        sheet.setCellValue("C1", "City");
        sheet.setCellValue("D1", "Salary");
        
        // 데이터 추가
        String[] names = {"John Doe", "Jane Smith", "Bob Johnson", "Alice Brown", "Charlie Wilson"};
        int[] ages = {30, 25, 35, 28, 42};
        String[] cities = {"New York", "Los Angeles", "Chicago", "Houston", "Phoenix"};
        double[] salaries = {50000, 60000, 55000, 52000, 65000};
        
        for (int i = 0; i < names.length; i++) {
            int row = i + 2; // 헤더 다음 행부터
            sheet.setCellValue("A" + row, names[i]);
            sheet.setCellValue("B" + row, ages[i]);
            sheet.setCellValue("C" + row, cities[i]);
            sheet.setCellValue("D" + row, salaries[i]);
        }
        
        // 압축 및 저장
        Path outputPath = Paths.get("simple_excel_demo.xlsx");
        
        try (MultiThreadedExcelCompressor compressor = new MultiThreadedExcelCompressor()) {
            CompressionResult result = compressor.compressWorkbook(workbook, outputPath);
            
            System.out.println("✓ Simple Excel file created successfully!");
            System.out.println("  File: " + outputPath.toAbsolutePath());
            System.out.println("  " + result.getDetailedSummary());
            System.out.println();
        }
    }
    
    /**
     * 다중 시트 Excel 파일 생성을 시연합니다.
     */
    private static void demonstrateMultiSheetExcelCreation() throws IOException {
        System.out.println("2. Multi-Sheet Excel File Creation Demo");
        System.out.println("=======================================");
        
        ExcelWorkbook workbook = new ExcelWorkbook();
        
        // 첫 번째 시트: 판매 데이터
        ExcelSheet salesSheet = workbook.createSheet("Sales Q1");
        salesSheet.setCellValue("A1", "Product");
        salesSheet.setCellValue("B1", "January");
        salesSheet.setCellValue("C1", "February");
        salesSheet.setCellValue("D1", "March");
        salesSheet.setCellValue("E1", "Total");
        
        String[] products = {"Laptop", "Mouse", "Keyboard", "Monitor", "Headphones"};
        int[][] salesData = {
            {100, 120, 110, 330},
            {200, 180, 220, 600},
            {150, 160, 140, 450},
            {80, 90, 85, 255},
            {120, 110, 130, 360}
        };
        
        for (int i = 0; i < products.length; i++) {
            int row = i + 2;
            salesSheet.setCellValue("A" + row, products[i]);
            salesSheet.setCellValue("B" + row, salesData[i][0]);
            salesSheet.setCellValue("C" + row, salesData[i][1]);
            salesSheet.setCellValue("D" + row, salesData[i][2]);
            salesSheet.setCellValue("E" + row, salesData[i][3]);
        }
        
        // 두 번째 시트: 직원 데이터
        ExcelSheet employeeSheet = workbook.createSheet("Employees");
        employeeSheet.setCellValue("A1", "Employee ID");
        employeeSheet.setCellValue("B1", "Name");
        employeeSheet.setCellValue("C1", "Department");
        employeeSheet.setCellValue("D1", "Hire Date");
        
        String[][] employeeData = {
            {"E001", "John Smith", "Engineering", "2020-01-15"},
            {"E002", "Sarah Johnson", "Marketing", "2019-03-22"},
            {"E003", "Mike Brown", "Sales", "2021-07-10"},
            {"E004", "Lisa Davis", "HR", "2018-11-05"},
            {"E005", "Tom Wilson", "Engineering", "2020-09-18"}
        };
        
        for (int i = 0; i < employeeData.length; i++) {
            int row = i + 2;
            for (int j = 0; j < employeeData[i].length; j++) {
                char col = (char) ('A' + j);
                employeeSheet.setCellValue(col + String.valueOf(row), employeeData[i][j]);
            }
        }
        
        // 세 번째 시트: 요약 데이터
        ExcelSheet summarySheet = workbook.createSheet("Summary");
        summarySheet.setCellValue("A1", "Metric");
        summarySheet.setCellValue("B1", "Value");
        summarySheet.setCellValue("A2", "Total Products");
        summarySheet.setCellValue("B2", products.length);
        summarySheet.setCellValue("A3", "Total Employees");
        summarySheet.setCellValue("B3", employeeData.length);
        summarySheet.setCellValue("A4", "Q1 Revenue");
        summarySheet.setCellValue("B4", 125000);
        
        // 압축 및 저장
        Path outputPath = Paths.get("multi_sheet_excel_demo.xlsx");
        
        try (MultiThreadedExcelCompressor compressor = new MultiThreadedExcelCompressor()) {
            CompressionResult result = compressor.compressWorkbook(workbook, outputPath);
            
            System.out.println("✓ Multi-sheet Excel file created successfully!");
            System.out.println("  File: " + outputPath.toAbsolutePath());
            System.out.println("  Sheets: " + workbook.getSheetCount());
            System.out.println("  " + result.getDetailedSummary());
            System.out.println();
        }
    }
    
    /**
     * 대용량 Excel 파일 생성을 시연합니다.
     */
    private static void demonstrateLargeExcelCreation() throws IOException {
        System.out.println("3. Large Excel File Creation Demo");
        System.out.println("=================================");
        
        ExcelWorkbook workbook = new ExcelWorkbook();
        
        // 대용량 데이터 시트 생성
        ExcelSheet largeSheet = workbook.createSheet("Large Dataset");
        
        // 헤더 생성
        String[] headers = {"ID", "Name", "Email", "Department", "Salary", "Start Date", "Performance", "Notes"};
        for (int i = 0; i < headers.length; i++) {
            char col = (char) ('A' + i);
            largeSheet.setCellValue(col + "1", headers[i]);
        }
        
        // 대량 데이터 생성 (5000행)
        System.out.println("Generating 5000 rows of data...");
        String[] departments = {"Engineering", "Marketing", "Sales", "HR", "Finance", "Operations"};
        String[] performanceRatings = {"Excellent", "Good", "Average", "Needs Improvement"};
        
        for (int row = 2; row <= 5001; row++) {
            int id = row - 1;
            largeSheet.setCellValue("A" + row, id);
            largeSheet.setCellValue("B" + row, "Employee " + id);
            largeSheet.setCellValue("C" + row, "employee" + id + "@company.com");
            largeSheet.setCellValue("D" + row, departments[id % departments.length]);
            largeSheet.setCellValue("E" + row, 40000 + (id % 50000)); // 40K-90K salary range
            largeSheet.setCellValue("F" + row, "2020-" + String.format("%02d", (id % 12) + 1) + "-01");
            largeSheet.setCellValue("G" + row, performanceRatings[id % performanceRatings.length]);
            largeSheet.setCellValue("H" + row, "Notes for employee " + id);
            
            if (row % 1000 == 0) {
                System.out.println("  Generated " + (row - 1) + " rows...");
            }
        }
        
        // 압축 및 저장
        Path outputPath = Paths.get("large_excel_demo.xlsx");
        
        System.out.println("Compressing large Excel file...");
        long startTime = System.nanoTime();
        
        try (MultiThreadedExcelCompressor compressor = new MultiThreadedExcelCompressor()) {
            CompressionResult result = compressor.compressWorkbook(workbook, outputPath);
            
            long endTime = System.nanoTime();
            Duration totalTime = Duration.ofNanos(endTime - startTime);
            
            System.out.println("✓ Large Excel file created successfully!");
            System.out.println("  File: " + outputPath.toAbsolutePath());
            System.out.println("  Rows: 5000 (plus header)");
            System.out.println("  Columns: " + headers.length);
            System.out.println("  Total cells: " + (5000 * headers.length));
            System.out.println("  " + result.getDetailedSummary());
            System.out.println("  Total time: " + totalTime);
            
            // 압축률 계산
            double compressionRatio = 1.0 - ((double) result.getCompressedSize() / result.getOriginalSize());
            System.out.println("  Compression ratio: " + String.format("%.2f%%", compressionRatio * 100));
            System.out.println();
        }
    }
    
    /**
     * 성능 비교를 시연합니다.
     */
    private static void demonstratePerformanceComparison() throws IOException {
        System.out.println("4. Performance Comparison Demo");
        System.out.println("=============================");
        
        // 테스트용 워크북 생성
        ExcelWorkbook workbook = createTestWorkbook();
        
        // 단일 스레드 테스트
        System.out.println("Testing with 1 thread...");
        Duration singleThreadTime = testCompressionPerformance(workbook, 1, "performance_single.xlsx");
        
        // 멀티 스레드 테스트
        int coreCount = Runtime.getRuntime().availableProcessors();
        System.out.println("Testing with " + coreCount + " threads...");
        Duration multiThreadTime = testCompressionPerformance(workbook, coreCount, "performance_multi.xlsx");
        
        // 결과 비교
        System.out.println("\nPerformance Comparison Results:");
        System.out.println("==============================");
        System.out.println("Single thread time: " + singleThreadTime);
        System.out.println("Multi thread time:  " + multiThreadTime);
        
        if (multiThreadTime.compareTo(singleThreadTime) < 0) {
            double improvement = ((double) singleThreadTime.toNanos() / multiThreadTime.toNanos() - 1) * 100;
            System.out.println("Performance improvement: " + String.format("%.1f%%", improvement));
        } else {
            System.out.println("Multi-threading overhead detected (normal for small datasets)");
        }
        
        System.out.println("\nNote: Performance benefits are more noticeable with larger datasets and more sheets.");
    }
    
    /**
     * 테스트용 워크북을 생성합니다.
     */
    private static ExcelWorkbook createTestWorkbook() {
        ExcelWorkbook workbook = new ExcelWorkbook();
        
        // 여러 시트 생성
        for (int sheetNum = 1; sheetNum <= 3; sheetNum++) {
            ExcelSheet sheet = workbook.createSheet("Sheet" + sheetNum);
            
            // 각 시트에 데이터 추가
            for (int row = 1; row <= 100; row++) {
                for (int col = 1; col <= 10; col++) {
                    String cellRef = ((char) ('A' + col - 1)) + String.valueOf(row);
                    if (col % 2 == 0) {
                        sheet.setCellValue(cellRef, "Data_" + sheetNum + "_" + row + "_" + col);
                    } else {
                        sheet.setCellValue(cellRef, row * col * sheetNum);
                    }
                }
            }
        }
        
        return workbook;
    }
    
    /**
     * 압축 성능을 테스트합니다.
     */
    private static Duration testCompressionPerformance(ExcelWorkbook workbook, int threadCount, String fileName) throws IOException {
        Path outputPath = Paths.get(fileName);
        
        long startTime = System.nanoTime();
        
        try (MultiThreadedExcelCompressor compressor = new MultiThreadedExcelCompressor(threadCount)) {
            CompressionResult result = compressor.compressWorkbook(workbook, outputPath);
            
            long endTime = System.nanoTime();
            Duration totalTime = Duration.ofNanos(endTime - startTime);
            
            System.out.println("  " + result.getDetailedSummary());
            System.out.println("  Total time: " + totalTime);
            
            return totalTime;
        }
    }
}