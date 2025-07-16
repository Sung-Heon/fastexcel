package com.github.rzymek.opczip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZipAssemblerTest {

    @TempDir
    Path tempDir;

    @Test
    void testAssembleZip() throws IOException {
        // Create test data
        String content1 = "Test file content 1";
        String content2 = "Another test file with different content";
        
        // Create compression results
        List<SingleFileCompressionResult> results = new ArrayList<>();
        results.add(createTestCompressionResult("file1.txt", content1.getBytes(StandardCharsets.UTF_8), 0));
        results.add(createTestCompressionResult("dir/file2.txt", content2.getBytes(StandardCharsets.UTF_8), 
                results.get(0).getTotalSize()));
        
        // Create output path
        Path outputPath = tempDir.resolve("test.zip");
        
        // Assemble ZIP
        ZipAssembler assembler = new ZipAssembler();
        assembler.assembleZip(results, outputPath);
        
        // Verify ZIP file exists and has content
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
        
        // Verify ZIP structure using Java's ZipInputStream
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(outputPath))) {
            // Check first entry
            ZipEntry entry = zis.getNextEntry();
            assertNotNull(entry);
            assertEquals("file1.txt", entry.getName());
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = zis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            assertEquals(content1, baos.toString(StandardCharsets.UTF_8.name()));
            
            // Check second entry
            entry = zis.getNextEntry();
            assertNotNull(entry);
            assertEquals("dir/file2.txt", entry.getName());
            
            baos = new ByteArrayOutputStream();
            while ((len = zis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            assertEquals(content2, baos.toString(StandardCharsets.UTF_8.name()));
            
            // Should be no more entries
            assertNull(zis.getNextEntry());
        }
    }
    
    @Test
    void testCalculateTotalSize() {
        // Create test data
        String content1 = "Test file content 1";
        String content2 = "Another test file with different content";
        
        // Create compression results
        List<SingleFileCompressionResult> results = new ArrayList<>();
        results.add(createTestCompressionResult("file1.txt", content1.getBytes(StandardCharsets.UTF_8), 0));
        results.add(createTestCompressionResult("dir/file2.txt", content2.getBytes(StandardCharsets.UTF_8), 
                results.get(0).getTotalSize()));
        
        // Calculate total size
        ZipAssembler assembler = new ZipAssembler();
        long totalSize = assembler.calculateTotalSize(results);
        
        // Calculate expected size manually
        long expectedSize = 0;
        for (SingleFileCompressionResult result : results) {
            expectedSize += result.getLocalHeader().length;
            expectedSize += result.getCompressedData().length;
            expectedSize += 46 + result.getEntryName().getBytes(StandardCharsets.UTF_8).length; // Central directory entry
        }
        expectedSize += 22; // EOCD
        
        assertEquals(expectedSize, totalSize);
    }
    
    @Test
    void testRecalculateOffsets() {
        // Create test data with incorrect offsets
        String content1 = "Test file content 1";
        String content2 = "Another test file with different content";
        
        // Create compression results with arbitrary offsets
        List<SingleFileCompressionResult> results = new ArrayList<>();
        results.add(createTestCompressionResult("file1.txt", content1.getBytes(StandardCharsets.UTF_8), 100)); // Wrong offset
        results.add(createTestCompressionResult("dir/file2.txt", content2.getBytes(StandardCharsets.UTF_8), 200)); // Wrong offset
        
        // Recalculate offsets
        ZipAssembler assembler = new ZipAssembler();
        List<SingleFileCompressionResult> updatedResults = assembler.recalculateOffsets(results);
        
        // Verify offsets are correct
        assertEquals(0, updatedResults.get(0).getLocalHeaderOffset());
        assertEquals(updatedResults.get(0).getTotalSize(), updatedResults.get(1).getLocalHeaderOffset());
    }
    
    @Test
    void testEmptyResultsList() {
        ZipAssembler assembler = new ZipAssembler();
        Path outputPath = tempDir.resolve("empty.zip");
        
        assertThrows(IllegalArgumentException.class, () -> {
            assembler.assembleZip(new ArrayList<>(), outputPath);
        });
    }
    
    @Test
    void testNullParameters() {
        ZipAssembler assembler = new ZipAssembler();
        Path outputPath = tempDir.resolve("null.zip");
        List<SingleFileCompressionResult> results = new ArrayList<>();
        results.add(createTestCompressionResult("file.txt", "test".getBytes(StandardCharsets.UTF_8), 0));
        
        assertThrows(NullPointerException.class, () -> {
            assembler.assembleZip(null, outputPath);
        });
        
        assertThrows(NullPointerException.class, () -> {
            assembler.assembleZip(results, null);
        });
    }
    
    /**
     * Helper method to create a test compression result.
     */
    private SingleFileCompressionResult createTestCompressionResult(String entryName, byte[] content, long offset) {
        try {
            // Calculate CRC32
            CRC32 crc = new CRC32();
            crc.update(content);
            long crcValue = crc.getValue();
            
            // Compress content
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
                dos.write(content);
            } finally {
                deflater.end();
            }
            byte[] compressedData = baos.toByteArray();
            
            // Create local header
            byte[] entryNameBytes = entryName.getBytes(StandardCharsets.UTF_8);
            byte[] localHeader = createTestLocalHeader(entryNameBytes, content.length, compressedData.length, crcValue);
            
            // Create DOS time (arbitrary value for testing)
            long dosTime = (1 << 25) | (1 << 21) | (1 << 16) | (1 << 11) | (1 << 5) | 0;
            
            return new SingleFileCompressionResult(
                entryName,
                localHeader,
                compressedData,
                crcValue,
                content.length,
                compressedData.length,
                dosTime,
                offset,
                Duration.ofMillis(100)
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test compression result", e);
        }
    }
    
    /**
     * Helper method to create a test local header.
     */
    private byte[] createTestLocalHeader(byte[] fileNameBytes, int uncompressedSize, 
                                       int compressedSize, long crcValue) {
        ByteBuffer buffer = ByteBuffer.allocate(30 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(0x04034b50); // Local file header signature
        buffer.putShort((short) 20); // Version needed to extract
        buffer.putShort((short) 0); // General purpose bit flag
        buffer.putShort((short) 8); // Compression method (DEFLATE)
        buffer.putInt(0); // Last mod file time & date (arbitrary for testing)
        buffer.putInt((int) crcValue); // CRC-32
        buffer.putInt(compressedSize); // Compressed size
        buffer.putInt(uncompressedSize); // Uncompressed size
        buffer.putShort((short) fileNameBytes.length); // File name length
        buffer.putShort((short) 0); // Extra field length
        buffer.put(fileNameBytes); // File name
        
        return buffer.array();
    }
}