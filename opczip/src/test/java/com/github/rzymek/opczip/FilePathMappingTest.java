package com.github.rzymek.opczip;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for file path mapping and validation functionality in MultiThreadedZipCompressor.
 * Tests requirements 6.1, 6.2, 6.3, and 6.4 from the specification.
 */
class FilePathMappingTest {

    @TempDir
    Path tempDir;
    
    private MultiThreadedZipCompressor compressor;
    private Path testFile1;
    private Path testFile2;
    private Path testFile3;
    private Path outputZip;

    @BeforeEach
    void setUp() throws IOException {
        compressor = new MultiThreadedZipCompressor(2);
        
        // Create test files
        testFile1 = tempDir.resolve("test1.txt");
        testFile2 = tempDir.resolve("test2.txt");
        testFile3 = tempDir.resolve("duplicate.txt");
        outputZip = tempDir.resolve("output.zip");
        
        Files.write(testFile1, "Test content 1".getBytes());
        Files.write(testFile2, "Test content 2".getBytes());
        Files.write(testFile3, "Test content 3".getBytes());
    }

    @Test
    void testDefaultPathGeneration() throws IOException {
        // Test requirement 6.2: default path generation for files without specified ZIP paths
        Collection<Path> sourceFiles = Arrays.asList(testFile1, testFile2);
        
        CompressionResult result = compressor.compressFiles(sourceFiles, outputZip);
        
        assertTrue(result.isSuccessful());
        assertEquals(2, result.getFileCount());
        assertTrue(Files.exists(outputZip));
        
        // Verify the ZIP file can be read and contains expected entries
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(outputZip))) {
            Set<String> entryNames = new HashSet<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
            
            assertTrue(entryNames.contains("test1.txt"));
            assertTrue(entryNames.contains("test2.txt"));
        }
    }

    @Test
    void testCustomZipPaths() throws IOException {
        // Test requirement 6.1: accept mapping of source file paths to ZIP internal paths
        Map<Path, String> fileMap = new LinkedHashMap<>();
        fileMap.put(testFile1, "custom/path1.txt");
        fileMap.put(testFile2, "another/path2.txt");
        
        CompressionResult result = compressor.compressFiles(fileMap, outputZip);
        
        assertTrue(result.isSuccessful());
        assertEquals(2, result.getFileCount());
        
        // Verify custom paths are used in the ZIP
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(outputZip))) {
            Set<String> entryNames = new HashSet<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
            
            assertTrue(entryNames.contains("custom/path1.txt"));
            assertTrue(entryNames.contains("another/path2.txt"));
        }
    }

    @Test
    void testDirectoryStructureCreation() throws IOException {
        // Test requirement 6.3: create necessary directory structure for nested paths
        Map<Path, String> fileMap = new LinkedHashMap<>();
        fileMap.put(testFile1, "level1/level2/level3/deep.txt");
        fileMap.put(testFile2, "level1/sibling.txt");
        fileMap.put(testFile3, "root.txt");
        
        CompressionResult result = compressor.compressFiles(fileMap, outputZip);
        
        assertTrue(result.isSuccessful());
        assertEquals(3, result.getFileCount());
        
        // Verify nested directory structure is created
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(outputZip))) {
            Set<String> entryNames = new HashSet<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
            
            assertTrue(entryNames.contains("level1/level2/level3/deep.txt"));
            assertTrue(entryNames.contains("level1/sibling.txt"));
            assertTrue(entryNames.contains("root.txt"));
        }
    }

    @Test
    void testDuplicatePathDetection() {
        // Test requirement 6.4: report error when internal paths conflict
        Map<Path, String> fileMap = new LinkedHashMap<>();
        fileMap.put(testFile1, "same/path.txt");
        fileMap.put(testFile2, "same/path.txt");  // Duplicate path
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        
        assertTrue(exception.getMessage().contains("Duplicate ZIP path"));
        assertTrue(exception.getMessage().contains("same/path.txt"));
    }

    @Test
    void testMixedNullAndCustomPaths() throws IOException {
        // Test mixing null paths (for default generation) with custom paths
        Map<Path, String> fileMap = new LinkedHashMap<>();
        fileMap.put(testFile1, null);  // Should use default path
        fileMap.put(testFile2, "custom/path.txt");  // Custom path
        fileMap.put(testFile3, null);  // Should use default path
        
        CompressionResult result = compressor.compressFiles(fileMap, outputZip);
        
        assertTrue(result.isSuccessful());
        assertEquals(3, result.getFileCount());
        
        // Verify both default and custom paths are used
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(outputZip))) {
            Set<String> entryNames = new HashSet<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
            
            assertTrue(entryNames.contains("test1.txt"));  // Default path
            assertTrue(entryNames.contains("custom/path.txt"));  // Custom path
            assertTrue(entryNames.contains("duplicate.txt"));  // Default path
        }
    }

    @Test
    void testDuplicateDefaultPathResolution() throws IOException {
        // Test that duplicate default paths are resolved with counters
        Path duplicateFile1 = tempDir.resolve("test1.txt");  // Same name as testFile1
        Path duplicateFile2 = tempDir.resolve("test1.txt");  // Another file with same name (different path)
        
        // Create files in different subdirectories to avoid filesystem conflicts
        Path subDir1 = tempDir.resolve("dir1");
        Path subDir2 = tempDir.resolve("dir2");
        Files.createDirectory(subDir1);
        Files.createDirectory(subDir2);
        
        duplicateFile1 = subDir1.resolve("test1.txt");
        duplicateFile2 = subDir2.resolve("test1.txt");
        
        Files.write(duplicateFile1, "Content 1".getBytes());
        Files.write(duplicateFile2, "Content 2".getBytes());
        
        // Use Collection method to trigger default path generation for all files
        Collection<Path> sourceFiles = Arrays.asList(testFile1, duplicateFile1, duplicateFile2);
        
        CompressionResult result = compressor.compressFiles(sourceFiles, outputZip);
        
        assertTrue(result.isSuccessful());
        assertEquals(3, result.getFileCount());
        
        // Verify that duplicate names are resolved with counters
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(outputZip))) {
            Set<String> entryNames = new HashSet<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
            
            // Should have original name and renamed versions
            assertTrue(entryNames.contains("test1.txt"));
            assertTrue(entryNames.contains("test1_1.txt"));
            assertTrue(entryNames.contains("test1_2.txt"));
        }
    }

    @Test
    void testInvalidZipPathValidation() {
        Map<Path, String> fileMap = new LinkedHashMap<>();
        
        // Test backslash validation
        fileMap.put(testFile1, "invalid\\path.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        assertTrue(exception.getMessage().contains("forward slashes"));
    }
    
    @Test
    void testPathTraversalValidation() {
        Map<Path, String> fileMap = new LinkedHashMap<>();
        
        // Test path traversal validation
        fileMap.put(testFile1, "../traversal.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        assertTrue(exception.getMessage().contains("parent directory references"));
    }
    
    @Test
    void testAbsolutePathValidation() {
        Map<Path, String> fileMap = new LinkedHashMap<>();
        
        // Test absolute path validation
        fileMap.put(testFile1, "/absolute/path.txt");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        assertTrue(exception.getMessage().contains("cannot be absolute"));
    }
    
    @Test
    void testEmptyPathValidation() {
        Map<Path, String> fileMap = new LinkedHashMap<>();
        
        // Test empty path validation
        fileMap.put(testFile1, "");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    void testReservedNameValidation() {
        Map<Path, String> fileMap = new LinkedHashMap<>();
        
        // Test Windows reserved names
        String[] reservedNames = {"CON.txt", "PRN.txt", "AUX.txt", "NUL.txt", "COM1.txt", "LPT1.txt"};
        
        for (String reservedName : reservedNames) {
            fileMap.clear();
            fileMap.put(testFile1, reservedName);
            
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                compressor.compressFiles(fileMap, outputZip);
            });
            assertTrue(exception.getMessage().contains("reserved name"));
        }
    }

    @Test
    void testPathLengthValidation() {
        Map<Path, String> fileMap = new LinkedHashMap<>();
        
        // Create a path that's too long (over 260 characters)
        StringBuilder longPath = new StringBuilder();
        for (int i = 0; i < 270; i++) {
            longPath.append("a");
        }
        longPath.append(".txt");
        
        fileMap.put(testFile1, longPath.toString());
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        assertTrue(exception.getMessage().contains("too long"));
    }

    @Test
    void testEmptyPathComponentValidation() {
        Map<Path, String> fileMap = new LinkedHashMap<>();
        fileMap.put(testFile1, "path//with//empty//components.txt");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        assertTrue(exception.getMessage().contains("empty path components"));
    }

    @Test
    void testFileDirectoryConflictValidation() {
        Map<Path, String> fileMap = new LinkedHashMap<>();
        fileMap.put(testFile1, "conflict");
        fileMap.put(testFile2, "conflict/file.txt");  // Creates directory conflict
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        assertTrue(exception.getMessage().contains("Path conflict"));
        assertTrue(exception.getMessage().contains("used both as a file and as a directory"));
    }

    @Test
    void testFileParentConflictValidation() {
        Map<Path, String> fileMap = new LinkedHashMap<>();
        fileMap.put(testFile1, "parent");
        fileMap.put(testFile2, "parent/child.txt");  // Child of another file
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        assertTrue(exception.getMessage().contains("Path conflict"));
        assertTrue(exception.getMessage().contains("used both as a file and as a directory"));
    }

    @Test
    void testPathNormalization() throws IOException {
        Map<Path, String> fileMap = new LinkedHashMap<>();
        fileMap.put(testFile1, "leading/and/trailing/slashes/");  // Remove leading slash for test
        fileMap.put(testFile2, "simple/path.txt");
        
        CompressionResult result = compressor.compressFiles(fileMap, outputZip);
        
        assertTrue(result.isSuccessful());
        assertEquals(2, result.getFileCount());
        
        // Verify paths are normalized (trailing slashes removed)
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(outputZip))) {
            Set<String> entryNames = new HashSet<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
            
            assertTrue(entryNames.contains("leading/and/trailing/slashes"));
            assertTrue(entryNames.contains("simple/path.txt"));
        }
    }

    @Test
    void testNonExistentFileValidation() {
        Path nonExistentFile = tempDir.resolve("does_not_exist.txt");
        Map<Path, String> fileMap = new LinkedHashMap<>();
        fileMap.put(nonExistentFile, "test.txt");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void testDirectoryAsSourceFileValidation() throws IOException {
        Path directory = tempDir.resolve("testdir");
        Files.createDirectory(directory);
        
        Map<Path, String> fileMap = new LinkedHashMap<>();
        fileMap.put(directory, "test.txt");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            compressor.compressFiles(fileMap, outputZip);
        });
        assertTrue(exception.getMessage().contains("not a regular file"));
    }
}