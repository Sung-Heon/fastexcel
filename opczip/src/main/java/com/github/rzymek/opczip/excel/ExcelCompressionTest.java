package com.github.rzymek.opczip.excel;

import com.github.rzymek.opczip.CompressionResult;
import com.github.rzymek.opczip.excel.ExcelSheet;
import com.github.rzymek.opczip.excel.ExcelWorkbook;
import com.github.rzymek.opczip.excel.MultiThreadedExcelCompressor;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Test class to verify Excel compression functionality.
 */
public class ExcelCompressionTest {
    
    public static void main(String[] args) {
        try {
            // Create a simple Excel workbook for testing
            ExcelWorkbook workbook = createTestWorkbook();
            
            // Set output path
            Path outputPath = Paths.get("test_excel_output.xlsx");
            
            // Test compression
            System.out.println("Starting Excel compression test...");
            
            try (MultiThreadedExcelCompressor compressor = new MultiThreadedExcelCompressor(2)) {
                CompressionResult result = compressor.compressWorkbook(workbook, outputPath);
                
                System.out.println("Compression completed successfully!");
                System.out.println("Result: " + result);
                System.out.println("Output file: " + outputPath.toAbsolutePath());
                
                // Verify file exists and has reasonable size
                if (java.nio.file.Files.exists(outputPath)) {
                    long fileSize = java.nio.file.Files.size(outputPath);
                    System.out.println("Generated file size: " + fileSize + " bytes");
                    
                    if (fileSize > 0) {
                        System.out.println("✓ File generated successfully with non-zero size");
                    } else {
                        System.err.println("✗ Generated file is empty");
                    }
                } else {
                    System.err.println("✗ Output file was not created");
                }
            }
            
        } catch (Exception e) {
            System.err.println("Test failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Creates a simple test workbook with sample data.
     */
    private static ExcelWorkbook createTestWorkbook() {
        ExcelWorkbook workbook = new ExcelWorkbook();
        
        // Create first sheet with sample data
        ExcelSheet sheet1 = new ExcelSheet("TestSheet1");
        sheet1.setCellValue("A1", "Hello");
        sheet1.setCellValue("B1", "World");
        sheet1.setCellValue("A2", 123);
        sheet1.setCellValue("B2", 456.78);
        sheet1.setCellValue("A3", "Test");
        sheet1.setCellValue("B3", "Data");
        workbook.addSheet(sheet1);
        
        // Create second sheet with different data
        ExcelSheet sheet2 = new ExcelSheet("TestSheet2");
        sheet2.setCellValue("A1", "Sheet2");
        sheet2.setCellValue("B1", "Content");
        sheet2.setCellValue("A2", 999);
        sheet2.setCellValue("B2", 123.45);
        workbook.addSheet(sheet2);
        
        return workbook;
    }
}
