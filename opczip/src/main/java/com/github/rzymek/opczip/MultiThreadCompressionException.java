package com.github.rzymek.opczip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Exception that aggregates multiple compression errors that occurred during multi-threaded compression.
 * This exception is used when multiple files fail to compress in parallel operations.
 */
public class MultiThreadCompressionException extends CompressionException {
    
    private final List<CompressionException> exceptions;
    
    /**
     * Creates a new MultiThreadCompressionException with the specified message and list of exceptions.
     *
     * @param message the detail message
     * @param exceptions the list of compression exceptions that occurred
     */
    public MultiThreadCompressionException(String message, List<CompressionException> exceptions) {
        super(message);
        this.exceptions = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(exceptions, "exceptions cannot be null"))
        );
    }
    
    /**
     * Creates a new MultiThreadCompressionException with a default message and list of exceptions.
     *
     * @param exceptions the list of compression exceptions that occurred
     */
    public MultiThreadCompressionException(List<CompressionException> exceptions) {
        this("Multiple errors occurred during multi-threaded compression", exceptions);
    }
    
    /**
     * @return an unmodifiable list of the compression exceptions that occurred
     */
    public List<CompressionException> getExceptions() {
        return exceptions;
    }
    
    /**
     * @return the number of exceptions that occurred
     */
    public int getExceptionCount() {
        return exceptions.size();
    }
    
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        sb.append(String.format(" (%d errors):", exceptions.size()));
        
        int count = 0;
        for (CompressionException exception : exceptions) {
            sb.append("\n  ").append(count + 1).append(") ");
            sb.append(exception.getMessage());
            count++;
            
            // Limit the number of detailed errors to avoid extremely long messages
            if (count >= 10 && exceptions.size() > 10) {
                sb.append("\n  ... and ").append(exceptions.size() - 10).append(" more errors");
                break;
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Returns a summary of the exceptions that occurred, grouped by compression stage.
     *
     * @return a string containing a summary of the exceptions
     */
    public String getErrorSummary() {
        int initErrors = 0;
        int readErrors = 0;
        int compressErrors = 0;
        int writeErrors = 0;
        int finalizeErrors = 0;
        
        for (CompressionException exception : exceptions) {
            switch (exception.getStage()) {
                case INITIALIZATION:
                    initErrors++;
                    break;
                case FILE_READING:
                    readErrors++;
                    break;
                case COMPRESSION:
                    compressErrors++;
                    break;
                case WRITING_TO_ZIP:
                    writeErrors++;
                    break;
                case FINALIZATION:
                    finalizeErrors++;
                    break;
            }
        }
        
        return String.format(
            "Compression errors summary: %d total errors (%d initialization, %d reading, " +
            "%d compression, %d writing, %d finalization)",
            exceptions.size(), initErrors, readErrors, compressErrors, writeErrors, finalizeErrors
        );
    }
}