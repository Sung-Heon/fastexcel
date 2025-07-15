package com.github.rzymek.opczip;

/**
 * Exception thrown when errors occur during the multi-threaded ZIP compression process.
 * This exception provides detailed information about what went wrong during compression.
 */
public class CompressionException extends RuntimeException {

    private final String entryName;
    private final CompressionStage stage;

    /**
     * Enumeration of stages in the compression process where errors might occur.
     */
    public enum CompressionStage {
        INITIALIZATION,
        FILE_READING,
        COMPRESSION,
        WRITING_TO_ZIP,
        FINALIZATION
    }

    /**
     * Creates a new CompressionException with the specified message.
     *
     * @param message the detail message
     */
    public CompressionException(String message) {
        super(message);
        this.entryName = null;
        this.stage = CompressionStage.INITIALIZATION;
    }

    /**
     * Creates a new CompressionException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public CompressionException(String message, Throwable cause) {
        super(message, cause);
        this.entryName = null;
        this.stage = CompressionStage.INITIALIZATION;
    }

    /**
     * Creates a new CompressionException with the specified message, entry name, and stage.
     *
     * @param message the detail message
     * @param entryName the name of the entry being processed when the error occurred
     * @param stage the stage of compression when the error occurred
     */
    public CompressionException(String message, String entryName, CompressionStage stage) {
        super(message);
        this.entryName = entryName;
        this.stage = stage;
    }

    /**
     * Creates a new CompressionException with the specified message, cause, entry name, and stage.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     * @param entryName the name of the entry being processed when the error occurred
     * @param stage the stage of compression when the error occurred
     */
    public CompressionException(String message, Throwable cause, String entryName, CompressionStage stage) {
        super(message, cause);
        this.entryName = entryName;
        this.stage = stage;
    }

    /**
     * @return the name of the entry being processed when the error occurred, or null if not applicable
     */
    public String getEntryName() {
        return entryName;
    }

    /**
     * @return the stage of compression when the error occurred
     */
    public CompressionStage getStage() {
        return stage;
    }

    @Override
    public String getMessage() {
        if (entryName != null) {
            return String.format("Error during %s stage while processing entry '%s': %s", 
                               stage, entryName, super.getMessage());
        }
        return super.getMessage();
    }
}