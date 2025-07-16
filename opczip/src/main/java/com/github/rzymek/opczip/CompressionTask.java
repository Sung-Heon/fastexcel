package com.github.rzymek.opczip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Represents a single file compression task that can be executed by worker threads.
 * Each task contains the source file path and the target entry name within the ZIP archive.
 * This class implements Callable to allow execution in a thread pool.
 */
public class CompressionTask implements Callable<SingleFileCompressionResult> {
    // ZIP format constants
    private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
    
    private final Path sourceFile;
    private final String entryName;
    private final long fileSize;
    private final long localHeaderOffset;
    private final int compressionLevel;
    private final int bufferSize;

    /**
     * Creates a new compression task.
     *
     * @param sourceFile the path to the source file to be compressed
     * @param entryName the name/path of the entry within the ZIP archive
     * @param fileSize the size of the source file in bytes
     * @param localHeaderOffset the offset where this entry's local header will be written
     * @throws IllegalArgumentException if sourceFile or entryName is null
     */
    public CompressionTask(Path sourceFile, String entryName, long fileSize, long localHeaderOffset) {
        this(sourceFile, entryName, fileSize, localHeaderOffset, Deflater.DEFAULT_COMPRESSION, 8192);
    }

    /**
     * Creates a new compression task with custom compression parameters.
     *
     * @param sourceFile the path to the source file to be compressed
     * @param entryName the name/path of the entry within the ZIP archive
     * @param fileSize the size of the source file in bytes
     * @param localHeaderOffset the offset where this entry's local header will be written
     * @param compressionLevel the compression level (0-9)
     * @param bufferSize the buffer size for compression
     * @throws IllegalArgumentException if sourceFile or entryName is null
     */
    public CompressionTask(Path sourceFile, String entryName, long fileSize, long localHeaderOffset, 
                          int compressionLevel, int bufferSize) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile cannot be null");
        this.entryName = Objects.requireNonNull(entryName, "entryName cannot be null");
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize cannot be negative");
        }
        this.fileSize = fileSize;
        this.localHeaderOffset = localHeaderOffset;
        
        if (compressionLevel < -1 || compressionLevel > 9) {
            throw new IllegalArgumentException("compressionLevel must be between -1 and 9 (-1 for default)");
        }
        this.compressionLevel = compressionLevel;
        
        if (bufferSize < 1024) {
            throw new IllegalArgumentException("bufferSize must be at least 1024 bytes");
        }
        this.bufferSize = bufferSize;
    }

    /**
     * Executes the compression task, reading the source file, compressing it,
     * and generating the necessary ZIP file structures.
     *
     * @return a SingleFileCompressionResult containing the compressed data and metadata
     * @throws CompressionException if an error occurs during compression
     */
    @Override
    public SingleFileCompressionResult call() {
        long startTime = System.nanoTime();
        byte[] fileData;
        byte[] compressedData;
        byte[] localHeader;
        long crcValue;
        long dosTime;
        
        try {
            // Read the source file
            try {
                fileData = Files.readAllBytes(sourceFile);
            } catch (IOException e) {
                throw new CompressionException("Failed to read source file", e, 
                                             entryName, CompressionException.CompressionStage.FILE_READING);
            }
            
            // Calculate CRC32
            CRC32 crc = new CRC32();
            crc.update(fileData);
            crcValue = crc.getValue();
            
            // Compress the file data
            try {
                compressedData = compress(fileData);
            } catch (IOException e) {
                throw new CompressionException("Failed to compress file data", e, 
                                             entryName, CompressionException.CompressionStage.COMPRESSION);
            }
            
            // Generate DOS time
            dosTime = toDosTime(LocalDateTime.now());
            
            // Create local file header
            byte[] entryNameBytes = entryName.getBytes(StandardCharsets.UTF_8);
            localHeader = createLocalFileHeader(entryNameBytes, fileData.length, compressedData.length, crcValue, dosTime);
            
            // Calculate compression time
            Duration compressionTime = Duration.ofNanos(System.nanoTime() - startTime);
            
            // Create and return the compression result with all necessary data for ZIP assembly
            return new SingleFileCompressionResult(
                entryName,                      // entryName
                localHeader,                    // localHeader
                compressedData,                 // compressedData
                crcValue,                       // crc32
                fileData.length,                // uncompressedSize
                compressedData.length,          // compressedSize
                dosTime,                        // dosTime
                localHeaderOffset,              // localHeaderOffset
                compressionTime                 // compressionTime
            );
            
        } catch (CompressionException e) {
            // Propagate CompressionException directly
            throw e;
        } catch (Exception e) {
            // Wrap other exceptions in CompressionException
            throw new CompressionException("Unexpected error during compression", e, 
                                         entryName, CompressionException.CompressionStage.COMPRESSION);
        }
    }
    
    /**
     * Compresses the input data using the DEFLATE algorithm.
     *
     * @param data the data to compress
     * @return the compressed data
     * @throws IOException if an I/O error occurs during compression
     */
    private byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(compressionLevel, true); // true = nowrap (no zlib header)
        
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater, bufferSize)) {
            dos.write(data);
        } finally {
            deflater.end(); // Release native resources
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Creates a local file header for the ZIP entry.
     *
     * @param fileNameBytes the entry name as UTF-8 bytes
     * @param uncompressedSize the uncompressed size of the file
     * @param compressedSize the compressed size of the file
     * @param crcValue the CRC32 checksum of the uncompressed file
     * @param dosTime the DOS time/date value
     * @return the local file header as a byte array
     */
    private byte[] createLocalFileHeader(byte[] fileNameBytes, int uncompressedSize, 
                                       int compressedSize, long crcValue, long dosTime) {
        ByteBuffer buffer = ByteBuffer.allocate(30 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(LOCAL_FILE_HEADER_SIGNATURE);
        buffer.putShort((short) 20);           // Version needed to extract
        buffer.putShort((short) 0);            // General purpose bit flag
        buffer.putShort((short) 8);            // Compression method (DEFLATE)
        buffer.putInt((int) dosTime);          // Last mod file time & date
        buffer.putInt((int) crcValue);         // CRC-32
        buffer.putInt(compressedSize);         // Compressed size
        buffer.putInt(uncompressedSize);       // Uncompressed size
        buffer.putShort((short) fileNameBytes.length); // File name length
        buffer.putShort((short) 0);            // Extra field length
        buffer.put(fileNameBytes);             // File name
        
        return buffer.array();
    }
    
    /**
     * Converts a LocalDateTime to MS-DOS time/date format.
     *
     * @param time the LocalDateTime to convert
     * @return the MS-DOS time/date value
     */
    private static long toDosTime(LocalDateTime time) {
        int year = time.getYear();
        if (year < 1980) year = 1980;
        if (year > 2107) year = 2107;
        
        return ((long) (year - 1980) << 25) |
               ((long) time.getMonthValue() << 21) |
               ((long) time.getDayOfMonth() << 16) |
               ((long) time.getHour() << 11) |
               ((long) time.getMinute() << 5) |
               ((long) time.getSecond() >> 1);
    }

    /**
     * @return the path to the source file
     */
    public Path getSourceFile() {
        return sourceFile;
    }

    /**
     * @return the entry name within the ZIP archive
     */
    public String getEntryName() {
        return entryName;
    }

    /**
     * @return the size of the source file in bytes
     */
    public long getFileSize() {
        return fileSize;
    }
    
    /**
     * @return the offset where this entry's local header will be written
     */
    public long getLocalHeaderOffset() {
        return localHeaderOffset;
    }
    
    /**
     * @return the compression level (0-9)
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }
    
    /**
     * @return the buffer size for compression
     */
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public String toString() {
        return String.format("CompressionTask{sourceFile=%s, entryName='%s', fileSize=%d, offset=%d}", 
                           sourceFile, entryName, fileSize, localHeaderOffset);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompressionTask that = (CompressionTask) o;
        return fileSize == that.fileSize &&
               localHeaderOffset == that.localHeaderOffset &&
               compressionLevel == that.compressionLevel &&
               bufferSize == that.bufferSize &&
               Objects.equals(sourceFile, that.sourceFile) &&
               Objects.equals(entryName, that.entryName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFile, entryName, fileSize, localHeaderOffset, compressionLevel, bufferSize);
    }
}