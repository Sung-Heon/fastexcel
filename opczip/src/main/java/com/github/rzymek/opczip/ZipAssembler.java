package com.github.rzymek.opczip;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/**
 * Assembles the final ZIP file from individual compression results.
 * This class is responsible for writing local headers and compressed data,
 * calculating offsets, creating central directory entries, and generating
 * the End of Central Directory (EOCD) record.
 */
public class ZipAssembler {
    // ZIP format constants
    private static final int CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
    private static final int END_OF_CENTRAL_DIR_SIGNATURE = 0x06054b50;
    
    /**
     * Assembles a ZIP file from a list of compression results.
     *
     * @param results the list of compression results to include in the ZIP
     * @param outputPath the path where the ZIP file will be written
     * @throws IOException if an I/O error occurs during assembly
     * @throws IllegalArgumentException if results is null or empty
     */
    public void assembleZip(List<SingleFileCompressionResult> results, Path outputPath) throws IOException {
        Objects.requireNonNull(results, "results cannot be null");
        Objects.requireNonNull(outputPath, "outputPath cannot be null");
        
        if (results.isEmpty()) {
            throw new IllegalArgumentException("results list cannot be empty");
        }
        
        // Create parent directories if they don't exist
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        
        try (FileChannel channel = FileChannel.open(outputPath, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.WRITE, 
                StandardOpenOption.TRUNCATE_EXISTING)) {
            
            // Write local headers and compressed data
            long currentOffset = 0;
            for (SingleFileCompressionResult result : results) {
                // Write local header
                channel.write(ByteBuffer.wrap(result.getLocalHeader()), currentOffset);
                currentOffset += result.getLocalHeader().length;
                
                // Write compressed data
                channel.write(ByteBuffer.wrap(result.getCompressedData()), currentOffset);
                currentOffset += result.getCompressedData().length;
            }
            
            // Remember where central directory starts
            long centralDirOffset = currentOffset;
            
            // Write central directory entries
            for (SingleFileCompressionResult result : results) {
                byte[] cdEntry = createCentralDirectoryEntry(result);
                channel.write(ByteBuffer.wrap(cdEntry), currentOffset);
                currentOffset += cdEntry.length;
            }
            
            // Calculate central directory size
            long centralDirSize = currentOffset - centralDirOffset;
            
            // Write End of Central Directory record
            byte[] eocd = createEOCD(results.size(), centralDirOffset, centralDirSize);
            channel.write(ByteBuffer.wrap(eocd), currentOffset);
        }
    }
    
    /**
     * Creates a central directory entry for a compression result.
     *
     * @param result the compression result to create an entry for
     * @return the central directory entry as a byte array
     */
    private byte[] createCentralDirectoryEntry(SingleFileCompressionResult result) {
        byte[] fileNameBytes = result.getEntryName().getBytes(StandardCharsets.UTF_8);
        
        ByteBuffer buffer = ByteBuffer.allocate(46 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(CENTRAL_DIRECTORY_SIGNATURE);
        buffer.putShort((short) 0x033F);        // Version made by (6.3 = ZIP 6.3)
        buffer.putShort((short) 20);            // Version needed to extract
        buffer.putShort((short) 0);             // General purpose bit flag
        buffer.putShort((short) 8);             // Compression method (DEFLATE)
        buffer.putInt((int) result.getDosTime()); // Last mod file time & date
        buffer.putInt((int) result.getCrc32()); // CRC-32
        
        // Handle sizes that might exceed 32-bit limits
        int compressedSize = (int) Math.min(result.getCompressedSize(), 0xFFFFFFFFL);
        int uncompressedSize = (int) Math.min(result.getUncompressedSize(), 0xFFFFFFFFL);
        
        buffer.putInt(compressedSize);          // Compressed size
        buffer.putInt(uncompressedSize);        // Uncompressed size
        buffer.putShort((short) fileNameBytes.length); // File name length
        buffer.putShort((short) 0);             // Extra field length
        buffer.putShort((short) 0);             // File comment length
        buffer.putShort((short) 0);             // Disk number start
        buffer.putShort((short) 0);             // Internal file attributes
        buffer.putInt(0);                       // External file attributes
        
        // Handle offset that might exceed 32-bit limits
        int localHeaderOffset = (int) Math.min(result.getLocalHeaderOffset(), 0xFFFFFFFFL);
        buffer.putInt(localHeaderOffset);       // Relative offset of local header
        
        buffer.put(fileNameBytes);              // File name
        
        return buffer.array();
    }
    
    /**
     * Creates the End of Central Directory (EOCD) record.
     *
     * @param entryCount the number of entries in the ZIP
     * @param cdOffset the offset of the central directory
     * @param cdSize the size of the central directory
     * @return the EOCD record as a byte array
     */
    private byte[] createEOCD(int entryCount, long cdOffset, long cdSize) {
        ByteBuffer buffer = ByteBuffer.allocate(22);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(END_OF_CENTRAL_DIR_SIGNATURE);
        buffer.putShort((short) 0);             // Number of this disk
        buffer.putShort((short) 0);             // Disk where central directory starts
        buffer.putShort((short) entryCount);    // Number of central directory records on this disk
        buffer.putShort((short) entryCount);    // Total number of central directory records
        
        // Handle sizes and offsets that might exceed 32-bit limits
        int centralDirSize = (int) Math.min(cdSize, 0xFFFFFFFFL);
        int centralDirOffset = (int) Math.min(cdOffset, 0xFFFFFFFFL);
        
        buffer.putInt(centralDirSize);          // Size of central directory
        buffer.putInt(centralDirOffset);        // Offset of start of central directory
        buffer.putShort((short) 0);             // Comment length
        
        return buffer.array();
    }
    
    /**
     * Calculates the total size of the ZIP file based on the compression results.
     * This includes local headers, compressed data, central directory, and EOCD.
     *
     * @param results the list of compression results
     * @return the total size of the ZIP file in bytes
     */
    public long calculateTotalSize(List<SingleFileCompressionResult> results) {
        long totalSize = 0;
        
        // Add size of local headers and compressed data
        for (SingleFileCompressionResult result : results) {
            totalSize += result.getLocalHeader().length;
            totalSize += result.getCompressedData().length;
        }
        
        // Add size of central directory entries
        for (SingleFileCompressionResult result : results) {
            totalSize += 46 + result.getEntryName().getBytes(StandardCharsets.UTF_8).length;
        }
        
        // Add size of EOCD
        totalSize += 22;
        
        return totalSize;
    }
    
    /**
     * Recalculates local header offsets for a list of compression results.
     * This is useful when the initial offset calculations need to be adjusted.
     *
     * @param results the list of compression results to recalculate offsets for
     * @return a new list of compression results with updated offsets
     */
    public List<SingleFileCompressionResult> recalculateOffsets(List<SingleFileCompressionResult> results) {
        long currentOffset = 0;
        List<SingleFileCompressionResult> updatedResults = new java.util.ArrayList<>(results.size());
        
        for (SingleFileCompressionResult result : results) {
            // Create a new result with the updated offset
            SingleFileCompressionResult updatedResult = new SingleFileCompressionResult(
                result.getEntryName(),
                result.getLocalHeader(),
                result.getCompressedData(),
                result.getCrc32(),
                result.getUncompressedSize(),
                result.getCompressedSize(),
                result.getDosTime(),
                currentOffset,
                result.getCompressionTime()
            );
            
            updatedResults.add(updatedResult);
            currentOffset += result.getTotalSize();
        }
        
        return updatedResults;
    }
}