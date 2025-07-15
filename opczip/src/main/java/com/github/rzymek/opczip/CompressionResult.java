package com.github.rzymek.opczip;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Holds compression metadata and results for multi-threaded ZIP compression operations.
 * This class encapsulates information about the compression process including timing,
 * file counts, sizes, and any errors that occurred during compression.
 */
public class CompressionResult {
    private final long originalSize;
    private final long compressedSize;
    private final int fileCount;
    private final Duration compressionTime;
    private final List<String> errors;
    private final boolean successful;
    private final int threadsUsed;

    /**
     * Creates a new CompressionResult with the specified parameters.
     *
     * @param originalSize the total size of uncompressed data in bytes
     * @param compressedSize the total size of compressed data in bytes
     * @param fileCount the number of files processed
     * @param compressionTime the time taken for compression
     * @param errors list of error messages, if any
     * @param successful whether the compression completed successfully
     * @param threadsUsed the number of threads used for compression
     */
    public CompressionResult(long originalSize, long compressedSize, int fileCount,
                           Duration compressionTime, List<String> errors, 
                           boolean successful, int threadsUsed) {
        this.originalSize = originalSize;
        this.compressedSize = compressedSize;
        this.fileCount = fileCount;
        this.compressionTime = Objects.requireNonNull(compressionTime, "compressionTime cannot be null");
        this.errors = errors != null ?
                Collections.unmodifiableList(new ArrayList<>(errors)) :
                Collections.emptyList();        this.successful = successful;
        this.threadsUsed = threadsUsed;
    }

    /**
     * @return the total size of uncompressed data in bytes
     */
    public long getOriginalSize() {
        return originalSize;
    }

    /**
     * @return the total size of compressed data in bytes
     */
    public long getCompressedSize() {
        return compressedSize;
    }

    /**
     * @return the number of files processed
     */
    public int getFileCount() {
        return fileCount;
    }

    /**
     * @return the time taken for compression
     */
    public Duration getCompressionTime() {
        return compressionTime;
    }

    /**
     * @return an immutable list of error messages
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * @return true if compression completed successfully, false otherwise
     */
    public boolean isSuccessful() {
        return successful;
    }

    /**
     * @return the number of threads used for compression
     */
    public int getThreadsUsed() {
        return threadsUsed;
    }

    /**
     * @return the compression ratio as a percentage (0-100)
     */
    public double getCompressionRatio() {
        if (originalSize == 0) {
            return 0.0;
        }
        return (1.0 - (double) compressedSize / originalSize) * 100.0;
    }

    /**
     * @return true if there were any errors during compression
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        return String.format(
            "CompressionResult{originalSize=%d, compressedSize=%d, fileCount=%d, " +
            "compressionTime=%s, successful=%s, threadsUsed=%d, compressionRatio=%.2f%%, errors=%d}",
            originalSize, compressedSize, fileCount, compressionTime, 
            successful, threadsUsed, getCompressionRatio(), errors.size()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompressionResult that = (CompressionResult) o;
        return originalSize == that.originalSize &&
               compressedSize == that.compressedSize &&
               fileCount == that.fileCount &&
               successful == that.successful &&
               threadsUsed == that.threadsUsed &&
               Objects.equals(compressionTime, that.compressionTime) &&
               Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalSize, compressedSize, fileCount, 
                          compressionTime, errors, successful, threadsUsed);
    }
}