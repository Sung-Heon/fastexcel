package com.github.rzymek.opczip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Simple test class to verify MultiThreadedZipCompressor functionality.
 * This is a basic test without JUnit dependencies.
 */
public class MultiThreadedZipCompressorTest {
    
    public static void main(String[] args) {
        try {
            testBasicCompression();
            testThreadCountConfiguration();
            testErrorHandling();
            testErrorCollectionLogic();
            testPartialFailureHandling();
            testResourceCleanupOnFailure();
            testGracefulShutdownOnErrors();
            testEmergencyShutdown();
            System.out.println("All tests passed!");
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void testBasicCompression() throws IOException {
        System.out.println("Testing basic compression...");
        
        // Create test files
        Path testFile1 = createTestFile("test1.txt", "This is test file 1 content.\nMultiple lines here.");
        Path testFile2 = createTestFile("test2.txt", "This is test file 2 content.\nDifferent content here.");
        
        try (MultiThreadedZipCompressor compressor = new MultiThreadedZipCompressor()) {
            Map<Path, String> fileMap = new HashMap<>();
            fileMap.put(testFile1, "folder1/test1.txt");
            fileMap.put(testFile2, "folder2/test2.txt");
            
            Path outputZip = Paths.get("test_output.zip");
            
            CompressionResult result = compressor.compressFiles(fileMap, outputZip);
            
            // Verify result
            assert result.isSuccessful() : "Compression should be successful";
            assert result.getFileCount() == 2 : "Should have compressed 2 files";
            assert result.getOriginalSize() > 0 : "Original size should be positive";
            assert result.getCompressedSize() > 0 : "Compressed size should be positive";
            assert result.getThreadsUsed() > 0 : "Should use at least 1 thread";
            
            // Verify ZIP file exists and is readable
            assert Files.exists(outputZip) : "Output ZIP should exist";
            verifyZipContents(outputZip, fileMap);
            
            // Cleanup
            Files.deleteIfExists(outputZip);
        } finally {
            Files.deleteIfExists(testFile1);
            Files.deleteIfExists(testFile2);
        }
        
        System.out.println("Basic compression test passed!");
    }
    
    private static void testThreadCountConfiguration() throws IOException {
        System.out.println("Testing thread count configuration...");
        
        // Test default constructor (should use CPU cores)
        try (MultiThreadedZipCompressor compressor1 = new MultiThreadedZipCompressor()) {
            int expectedThreads = Runtime.getRuntime().availableProcessors();
            assert compressor1.getThreadCount() == expectedThreads : 
                "Default thread count should equal CPU cores";
        }
        
        // Test custom thread count
        try (MultiThreadedZipCompressor compressor2 = new MultiThreadedZipCompressor(4)) {
            assert compressor2.getThreadCount() == 4 : "Custom thread count should be 4";
        }
        
        // Test invalid thread count
        try {
            new MultiThreadedZipCompressor(0);
            assert false : "Should throw exception for invalid thread count";
        } catch (IllegalArgumentException e) {
            // Expected
        }
        
        System.out.println("Thread count configuration test passed!");
    }
    
    private static void testErrorHandling() throws IOException {
        System.out.println("Testing error handling...");
        
        try (MultiThreadedZipCompressor compressor = new MultiThreadedZipCompressor()) {
            // Test with non-existent file
            Map<Path, String> fileMap = new HashMap<>();
            fileMap.put(Paths.get("non_existent_file.txt"), "test.txt");
            
            try {
                compressor.compressFiles(fileMap, Paths.get("output.zip"));
                assert false : "Should throw exception for non-existent file";
            } catch (IllegalArgumentException e) {
                // Expected
            }
            
            // Test with null parameters
            try {
                compressor.compressFiles((Map<Path, String>) null, Paths.get("output.zip"));
                assert false : "Should throw exception for null file map";
            } catch (NullPointerException e) {
                // Expected
            }
            
            try {
                compressor.compressFiles(new HashMap<>(), null);
                assert false : "Should throw exception for null output path";
            } catch (NullPointerException e) {
                // Expected
            }
            
            // Test with empty file map
            try {
                compressor.compressFiles(new HashMap<>(), Paths.get("output.zip"));
                assert false : "Should throw exception for empty file map";
            } catch (IllegalArgumentException e) {
                // Expected
            }
        }
        
        System.out.println("Error handling test passed!");
    }
    
    private static void testErrorCollectionLogic() throws IOException {
        System.out.println("Testing error collection logic...");
        
        try (MultiThreadedZipCompressor compressor = new MultiThreadedZipCompressor(2)) {
            // Create a mix of valid and invalid files
            Path validFile = createTestFile("valid_file.txt", "Valid content");
            Path invalidFile = Paths.get("non_existent_file.txt");
            
            Map<Path, String> fileMap = new HashMap<>();
            fileMap.put(validFile, "valid.txt");
            fileMap.put(invalidFile, "invalid.txt");
            
            try {
                compressor.compressFiles(fileMap, Paths.get("test_error_collection.zip"));
                assert false : "Should throw MultiThreadCompressionException";
            } catch (IllegalArgumentException e) {
                // Expected - validation should catch non-existent files
                assert e.getMessage().contains("does not exist") : 
                    "Error message should mention non-existent file";
            } finally {
                Files.deleteIfExists(validFile);
                Files.deleteIfExists(Paths.get("test_error_collection.zip"));
            }
        }
        
        System.out.println("Error collection logic test passed!");
    }
    
    private static void testPartialFailureHandling() throws IOException {
        System.out.println("Testing partial failure handling...");
        
        // Test CompressionResult factory methods for failed operations
        java.time.Duration testDuration = java.time.Duration.ofSeconds(5);
        java.util.List<String> errors = java.util.Arrays.asList("Error 1", "Error 2");
        
        // Test failed result creation
        CompressionResult failedResult = CompressionResult.createFailedResult(
            1000L, testDuration, errors, 2);
        
        assert !failedResult.isSuccessful() : "Failed result should not be successful";
        assert failedResult.getFileCount() == 0 : "Failed result should have 0 files processed";
        assert failedResult.getCompressedSize() == 0 : "Failed result should have 0 compressed size";
        assert failedResult.hasErrors() : "Failed result should have errors";
        assert failedResult.getErrors().size() == 2 : "Failed result should have 2 errors";
        
        // Test partial result creation
        CompressionResult partialResult = CompressionResult.createPartialResult(
            2000L, 800L, 1, testDuration, errors, 2);
        
        assert !partialResult.isSuccessful() : "Partial result should not be successful";
        assert partialResult.getFileCount() == 1 : "Partial result should have 1 file processed";
        assert partialResult.getCompressedSize() == 800L : "Partial result should have correct compressed size";
        assert partialResult.hasErrors() : "Partial result should have errors";
        
        // Test detailed summary
        String summary = failedResult.getDetailedSummary();
        assert summary.contains("FAILED") : "Summary should contain FAILED status";
        assert summary.contains("Error 1") : "Summary should contain first error";
        assert summary.contains("Error 2") : "Summary should contain second error";
        
        System.out.println("Partial failure handling test passed!");
    }
    
    private static void testResourceCleanupOnFailure() throws IOException {
        System.out.println("Testing resource cleanup on failure...");
        
        Path outputZip = Paths.get("test_cleanup_failure.zip");
        
        try (MultiThreadedZipCompressor compressor = new MultiThreadedZipCompressor(1)) {
            // Test with duplicate ZIP paths (should cause validation error)
            Path file1 = createTestFile("file1.txt", "Content 1");
            Path file2 = createTestFile("file2.txt", "Content 2");
            
            Map<Path, String> fileMap = new HashMap<>();
            fileMap.put(file1, "same_path.txt");
            fileMap.put(file2, "same_path.txt");  // Duplicate path
            
            try {
                compressor.compressFiles(fileMap, outputZip);
                assert false : "Should throw exception for duplicate ZIP paths";
            } catch (IllegalArgumentException e) {
                // Expected - should catch duplicate paths
                assert e.getMessage().contains("Duplicate ZIP path") : 
                    "Error should mention duplicate path";
                
                // Verify no partial output file was created
                assert !Files.exists(outputZip) : 
                    "No partial output file should exist after validation failure";
            } finally {
                Files.deleteIfExists(file1);
                Files.deleteIfExists(file2);
                Files.deleteIfExists(outputZip);
            }
        }
        
        System.out.println("Resource cleanup on failure test passed!");
    }
    
    private static void testGracefulShutdownOnErrors() throws IOException {
        System.out.println("Testing graceful shutdown on errors...");
        
        MultiThreadedZipCompressor compressor = new MultiThreadedZipCompressor(2);
        
        // Verify compressor is not closed initially
        assert !compressor.isClosed() : "Compressor should not be closed initially";
        
        // Test normal close
        compressor.close();
        assert compressor.isClosed() : "Compressor should be closed after close()";
        
        // Test that operations fail after close
        try {
            Path testFile = createTestFile("test_after_close.txt", "Content");
            Map<Path, String> fileMap = new HashMap<>();
            fileMap.put(testFile, "test.txt");
            
            compressor.compressFiles(fileMap, Paths.get("output.zip"));
            assert false : "Should throw exception when using closed compressor";
        } catch (IllegalStateException e) {
            // Expected
            assert e.getMessage().contains("closed") : 
                "Error should mention compressor is closed";
        }
        
        // Test double close (should be safe)
        compressor.close(); // Should not throw
        
        System.out.println("Graceful shutdown on errors test passed!");
    }
    
    private static void testEmergencyShutdown() throws IOException {
        System.out.println("Testing emergency shutdown...");
        
        MultiThreadedZipCompressor compressor = new MultiThreadedZipCompressor(2);
        
        // Test emergency shutdown
        compressor.emergencyShutdown();
        assert compressor.isClosed() : "Compressor should be closed after emergency shutdown";
        
        // Test that operations fail after emergency shutdown
        try {
            Path testFile = createTestFile("test_after_emergency.txt", "Content");
            Map<Path, String> fileMap = new HashMap<>();
            fileMap.put(testFile, "test.txt");
            
            compressor.compressFiles(fileMap, Paths.get("output.zip"));
            assert false : "Should throw exception after emergency shutdown";
        } catch (IllegalStateException e) {
            // Expected
            assert e.getMessage().contains("closed") : 
                "Error should mention compressor is closed";
        } finally {
            Files.deleteIfExists(Paths.get("test_after_emergency.txt"));
        }
        
        System.out.println("Emergency shutdown test passed!");
    }
    
    private static Path createTestFile(String name, String content) throws IOException {
        Path file = Paths.get(name);
        Files.write(file, content.getBytes());
        return file;
    }
    
    private static void verifyZipContents(Path zipFile, Map<Path, String> expectedFiles) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            Map<String, String> foundEntries = new HashMap<>();
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                foundEntries.put(entry.getName(), "found");
            }
            
            // Verify all expected files are present
            for (String expectedPath : expectedFiles.values()) {
                assert foundEntries.containsKey(expectedPath) : 
                    "ZIP should contain entry: " + expectedPath;
            }
            
            assert foundEntries.size() == expectedFiles.size() : 
                "ZIP should contain exactly " + expectedFiles.size() + " entries";
        }
    }
}