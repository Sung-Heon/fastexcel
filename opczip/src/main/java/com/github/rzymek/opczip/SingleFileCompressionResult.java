package com.github.rzymek.opczip;

import java.time.Duration;
import java.util.Objects;

/**
 * Holds the result of compressing a single file, including the compressed data,
 * local file header, and metadata needed for ZIP assembly.
 * This class is used by CompressionTask to return all necessary information
 * for the ZipAssembler to create the final ZIP file.
 */
public class SingleFileCompressionResult extends CompressionResult {
    private final String entryName;
    private final byte[] localHeader;
    private final byte[] compressedData;
    private final long crc32;
    private final long uncompressedSize;
    private final long compressedSize;
    private final long dosTime;
    private long localHeaderOffset;

    /**
     * Creates a new SingleFileCompressionResult with the specified parameters.
     *
     * @param entryName the name of the entry within the ZIP archive
     * @param localHeader the local file header for this entry (can be null if ZipAssembler will create it)
     * @param compressedData the compressed file data
     * @param crc32 the CRC32 checksum of the uncompressed file
     * @param uncompressedSize the uncompressed size of the file
     * @param compressedSize the compressed size of the file
     * @param dosTime the DOS time/date value for the file
     * @param localHeaderOffset the offset where this entry's local header will be written
     * @param compressionTime the time taken for compression
     */
    public SingleFileCompressionResult(String entryName, byte[] localHeader, byte[] compressedData,
                                     long crc32, long uncompressedSize, long compressedSize,
                                     long dosTime, long localHeaderOffset, Duration compressionTime) {
        super(uncompressedSize, compressedSize, 1, compressionTime, null, true, 1);
        this.entryName = Objects.requireNonNull(entryName, "entryName cannot be null");
        this.localHeader = localHeader; // Allow null - ZipAssembler will create if needed
        this.compressedData = Objects.requireNonNull(compressedData, "compressedData cannot be null");
        this.crc32 = crc32;
        this.uncompressedSize = uncompressedSize;
        this.compressedSize = compressedSize;
        this.dosTime = dosTime;
        this.localHeaderOffset = localHeaderOffset;
    }

    /**
     * @return the name of the entry within the ZIP archive
     */
    public String getEntryName() {
        return entryName;
    }

    /**
     * @return the local file header for this entry
     */
    public byte[] getLocalHeader() {
        return localHeader;
    }

    /**
     * @return the compressed file data
     */
    public byte[] getCompressedData() {
        return compressedData;
    }

    /**
     * @return the CRC32 checksum of the uncompressed file
     */
    public long getCrc32() {
        return crc32;
    }

    /**
     * @return the uncompressed size of the file
     */
    public long getUncompressedSize() {
        return uncompressedSize;
    }

    /**
     * @return the compressed size of the file
     */
    public long getCompressedSize() {
        return compressedSize;
    }

    /**
     * @return the DOS time/date value for the file
     */
    public long getDosTime() {
        return dosTime;
    }

    /**
     * @return the offset where this entry's local header will be written
     */
    public long getLocalHeaderOffset() {
        return localHeaderOffset;
    }

    /**
     * Sets the offset where this entry's local header will be written.
     * @param localHeaderOffset the offset where this entry's local header will be written
     */
    public void setLocalHeaderOffset(long localHeaderOffset) {
        this.localHeaderOffset = localHeaderOffset;
    }

    /**
     * @return the total size of this entry in the ZIP file (local header + compressed data)
     */
    public long getTotalSize() {
        return (localHeader != null ? localHeader.length : 0) + compressedData.length;
    }

    @Override
    public String toString() {
        return String.format(
            "SingleFileCompressionResult{entryName='%s', uncompressedSize=%d, compressedSize=%d, " +
            "crc32=%d, localHeaderOffset=%d, compressionRatio=%.2f%%}",
            entryName, uncompressedSize, compressedSize, crc32, localHeaderOffset, getCompressionRatio()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SingleFileCompressionResult that = (SingleFileCompressionResult) o;
        return crc32 == that.crc32 &&
               uncompressedSize == that.uncompressedSize &&
               compressedSize == that.compressedSize &&
               dosTime == that.dosTime &&
               localHeaderOffset == that.localHeaderOffset &&
               Objects.equals(entryName, that.entryName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entryName, crc32, uncompressedSize, 
                          compressedSize, dosTime, localHeaderOffset);
    }
}