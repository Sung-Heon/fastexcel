package com.github.rzymek.opczip;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a single file compression task that can be executed by worker threads.
 * Each task contains the source file path and the target entry name within the ZIP archive.
 */
public class CompressionTask {
    private final Path sourceFile;
    private final String entryName;
    private final long fileSize;

    /**
     * Creates a new compression task.
     *
     * @param sourceFile the path to the source file to be compressed
     * @param entryName the name/path of the entry within the ZIP archive
     * @param fileSize the size of the source file in bytes
     * @throws IllegalArgumentException if sourceFile or entryName is null
     */
    public CompressionTask(Path sourceFile, String entryName, long fileSize) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile cannot be null");
        this.entryName = Objects.requireNonNull(entryName, "entryName cannot be null");
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize cannot be negative");
        }
        this.fileSize = fileSize;
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

    @Override
    public String toString() {
        return String.format("CompressionTask{sourceFile=%s, entryName='%s', fileSize=%d}", 
                           sourceFile, entryName, fileSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompressionTask that = (CompressionTask) o;
        return fileSize == that.fileSize &&
               Objects.equals(sourceFile, that.sourceFile) &&
               Objects.equals(entryName, that.entryName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFile, entryName, fileSize);
    }
}