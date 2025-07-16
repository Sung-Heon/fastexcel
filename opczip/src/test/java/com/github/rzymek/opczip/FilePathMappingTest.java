package com.github.rzymek.opczip;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for file path mapping and validation functionality in MultiThreadedZipCompressor.
 * This class tests all aspects of requirement 6: file path mapping and validation.
 */
class FilePathMappingTest {

    @TempDir
    Path tempDir;
    
    private Path testFile1;
    private Path testFile2;
    private Path testFile3;
    private MultiThreadedZipCompressor compressor;

    @BeforeEach
    void setUp() throws IOException {
        // Create test files
        testFile1 = tempDir.resolve("file1.txt");
        testFile2 = tempDir.resolve("file2.txt");
        testFile3 = tempDir.resolve("file3.txt");
        
        Files.write(testFile1, "Content of file 1".getBytes());
        Files.write(testFile2, "Content of file 2".getBytes());
        Files.write(testFile3, "Content of file 3".getBytes());
        
        compressor = new MultiThreadedZipCompressor(2);
    }

    /**
     * Test requirement 6.1: Accept mapping of source file paths to ZIP internal paths
     */
    @Test
    void testCustomZipPathMapping() throws IOException {
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, "custom/path/file1.txt");
        fileMapping.put(testFile2, "another/location/file2.txt");
        
        Path outputZip = tempDir.resolve("test.zip");
        
        // Should complete without errors
        assertDoesNotThrow(() -> {
            CompressionResult result = compressor.compressFiles(fileMapping, outputZip);
            assertTrue(result.isSuccessful());
            assertEquals(2, result.getFileCount());
        });
        
        assertTrue(Files.exists(outputZip));
    }

    /**
     * Test requirement 6.2: Use source file's relative path as default when no internal path specified
     */
    @Test
    void testDefaultPathGeneration() throws IOException {
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, null); // Should use default path
        fileMapping.put(testFile2, null); // Should use default path
        
        Path outputZip = tempDir.resolve("test.zip");
        
        // Should complete without errors and use default paths
        assertDoesNotThrow(() -> {
            CompressionResult result = compressor.compressFiles(fileMapping, outputZip);
            assertTrue(result.isSuccessful());
            assertEquals(2, result.getFileCount());
        });
        
        assertTrue(Files.exists(outputZip));
    }

    /**
     * Test requirement 6.3: Create necessary directory structure for nested paths
     */
    @Test
    void testNestedDirectoryStructure() throws IOException {
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, "level1/level2/level3/file1.txt");
        fileMapping.put(testFile2, "level1/different/path/file2.txt");
        fileMapping.put(testFile3, "root_file.txt");
        
        Path outputZip = tempDir.resolve("test.zip");
        
        // Should handle nested directory structure correctly
        assertDoesNotThrow(() -> {
            CompressionResult result = compressor.compressFiles(fileMapping, outputZip);
            assertTrue(result.isSuccessful());
            assertEquals(3, result.getFileCount());
        });
        
        assertTrue(Files.exists(outputZip));
    }

    /**
     * Test requirement 6.4: Report error when internal paths conflict
     */
    @Test
    void testDuplicatePathDetection() throws IOException {
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, "same/path/file.txt");
        fileMapping.put(testFile2, "same/path/file.txt"); // Duplicate path
        
        Path outputZip = tempDir.resolve("test.zip");
        
        // Should throw exception due to duplicate paths
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMapping, outputZip);
        });
        
        assertTrue(exception.getMessage().contains("Duplicate ZIP path"));
    }

    /**
     * Test path normalization and validation
     */
    @Test
    void testPathNormalization() throws IOException {
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, "  leading/spaces/file.txt  "); // Should normalize by trimming spaces
        fileMapping.put(testFile2, "trailing/slash/file.txt/"); // Should normalize by removing trailing slash
        
        Path outputZip = tempDir.resolve("test.zip");
        
        // Should complete after normalization
        assertDoesNotThrow(() -> {
            CompressionResult result = compressor.compressFiles(fileMapping, outputZip);
            assertTrue(result.isSuccessful());
            assertEquals(2, result.getFileCount());
        });
        
        assertTrue(Files.exists(outputZip));
    }

    /**
     * Test invalid path characters and security validation
     */
    @Test
    void testInvalidPathValidation() throws IOException {
        Path outputZip = tempDir.resolve("test.zip");
        
        // Test backslash in path
        Map<Path, String> backslashMapping = new HashMap<>();
        backslashMapping.put(testFile1, "path\\with\\backslashes.txt");
        
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(backslashMapping, outputZip);
        });
        assertTrue(exception1.getMessage().contains("forward slashes"));
        
        // Test path traversal attempt
        Map<Path, String> traversalMapping = new HashMap<>();
        traversalMapping.put(testFile1, "../../../etc/passwd");
        
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(traversalMapping, outputZip);
        });
        assertTrue(exception2.getMessage().contains("parent directory references"));
    }

    /**
     * Test directory-file path conflicts
     */
    @Test
    void testDirectoryFileConflicts() throws IOException {
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, "folder/file.txt");
        fileMapping.put(testFile2, "folder"); // This creates a conflict - folder is both a directory and a file
        
        Path outputZip = tempDir.resolve("test.zip");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMapping, outputZip);
        });
        
        assertTrue(exception.getMessage().contains("Path conflict"));
    }

    /**
     * Test file path prefix conflicts
     */
    @Test
    void testFilePathPrefixConflicts() throws IOException {
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, "parent");
        fileMapping.put(testFile2, "parent/child.txt"); // parent is both a file and a directory
        
        Path outputZip = tempDir.resolve("test.zip");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMapping, outputZip);
        });
        
        assertTrue(exception.getMessage().contains("Path conflict"));
    }

    /**
     * Test reserved filename validation
     */
    @Test
    void testReservedFilenames() throws IOException {
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, "CON.txt"); // Reserved Windows filename
        
        Path outputZip = tempDir.resolve("test.zip");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMapping, outputZip);
        });
        
        assertTrue(exception.getMessage().contains("reserved name"));
    }

    /**
     * Test default path generation with conflicts
     */
    @Test
    void testDefaultPathGenerationWithConflicts() throws IOException {
        // Create files with same name in different directories
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        Path duplicateFile = subDir.resolve("file1.txt");
        Files.write(duplicateFile, "Duplicate content".getBytes());
        
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, null); // file1.txt
        fileMapping.put(duplicateFile, null); // also file1.txt - should get renamed
        
        Path outputZip = tempDir.resolve("test.zip");
        
        // Should handle the conflict by renaming the second file
        assertDoesNotThrow(() -> {
            CompressionResult result = compressor.compressFiles(fileMapping, outputZip);
            assertTrue(result.isSuccessful());
            assertEquals(2, result.getFileCount());
        });
        
        assertTrue(Files.exists(outputZip));
    }

    /**
     * Test empty and null path validation
     */
    @Test
    void testEmptyAndNullPathValidation() throws IOException {
        Path outputZip = tempDir.resolve("test.zip");
        
        // Test empty string path
        Map<Path, String> emptyMapping = new HashMap<>();
        emptyMapping.put(testFile1, "");
        
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(emptyMapping, outputZip);
        });
        assertTrue(exception1.getMessage().contains("ZIP path cannot be null or empty for file"));
        
        // Test whitespace-only path
        Map<Path, String> whitespaceMapping = new HashMap<>();
        whitespaceMapping.put(testFile1, "   ");
        
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(whitespaceMapping, outputZip);
        });
        assertTrue(exception2.getMessage().contains("ZIP path cannot be null or empty for file"));
    }

    /**
     * Test path length validation
     */
    @Test
    void testPathLengthValidation() throws IOException {
        // Create a very long path (over 260 characters)
        StringBuilder longPath = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longPath.append("verylongdirectoryname/");
        }
        longPath.append("file.txt");
        
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, longPath.toString());
        
        Path outputZip = tempDir.resolve("test.zip");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMapping, outputZip);
        });
        
        assertTrue(exception.getMessage().contains("too long"));
    }

    /**
     * Test mixed valid and invalid scenarios
     */
    @Test
    void testMixedValidInvalidScenarios() throws IOException {
        Map<Path, String> fileMapping = new HashMap<>();
        fileMapping.put(testFile1, "valid/path/file1.txt");
        fileMapping.put(testFile2, null); // Should use default
        fileMapping.put(testFile3, "another/valid/path/file3.txt");
        
        Path outputZip = tempDir.resolve("test.zip");
        
        // Should complete successfully with mixed explicit and default paths
        assertDoesNotThrow(() -> {
            CompressionResult result = compressor.compressFiles(fileMapping, outputZip);
            assertTrue(result.isSuccessful());
            assertEquals(3, result.getFileCount());
        });
        
        assertTrue(Files.exists(outputZip));
    }
}