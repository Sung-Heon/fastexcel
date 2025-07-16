package com.github.rzymek.opczip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Multi-threaded ZIP compressor that distributes file compression tasks across multiple worker threads.
 * Each thread compresses files independently, and the results are assembled into a final ZIP file.
 * 
 * This class provides configurable thread pool management and handles error aggregation across
 * multiple threads. The compression process follows a two-phase approach:
 * 1. Compression Phase: Multiple threads compress files and generate local headers with metadata
 * 2. Assembly Phase: Single thread combines all results and writes central directory and EOCD
 */
public class MultiThreadedZipCompressor implements AutoCloseable {
    
    private final int threadCount;
    private final ExecutorService executorService;
    private final ZipAssembler zipAssembler;
    private volatile boolean closed = false;
    
    /**
     * Creates a new MultiThreadedZipCompressor with the default number of threads
     * (equal to the number of available CPU cores).
     */
    public MultiThreadedZipCompressor() {
        this(Runtime.getRuntime().availableProcessors());
    }
    
    /**
     * Creates a new MultiThreadedZipCompressor with the specified number of threads.
     *
     * @param threadCount the number of worker threads to use for compression
     * @throws IllegalArgumentException if threadCount is less than 1
     */
    public MultiThreadedZipCompressor(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("Thread count must be at least 1");
        }
        
        this.threadCount = threadCount;
        this.executorService = createExecutorService(threadCount);
        this.zipAssembler = new ZipAssembler();
    }
    
    /**
     * Creates the executor service for managing worker threads.
     *
     * @param threadCount the number of threads in the pool
     * @return a configured ExecutorService
     */
    private ExecutorService createExecutorService(int threadCount) {
        return new ThreadPoolExecutor(
            threadCount,                    // corePoolSize
            threadCount,                    // maximumPoolSize
            60L,                           // keepAliveTime
            TimeUnit.SECONDS,              // timeUnit
            new LinkedBlockingQueue<>(),   // workQueue
            new ThreadFactory() {          // threadFactory
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ZipCompressor-" + (++counter));
                    t.setDaemon(false);
                    return t;
                }
            }
        );
    }
    
    /**
     * Compresses multiple files into a ZIP archive using multiple threads with default ZIP paths.
     * This convenience method generates default ZIP paths for all files based on their filenames.
     *
     * @param sourceFiles collection of source file paths to compress
     * @param outputZip the path where the ZIP file will be written
     * @return a CompressionResult containing compression statistics and metadata
     * @throws IOException if an I/O error occurs during compression or assembly
     * @throws MultiThreadCompressionException if multiple compression errors occur
     * @throws IllegalStateException if the compressor has been closed
     * @throws IllegalArgumentException if parameters are invalid
     */
    public CompressionResult compressFiles(Collection<Path> sourceFiles, Path outputZip) 
            throws IOException {
        
        Objects.requireNonNull(sourceFiles, "sourceFiles cannot be null");
        
        if (sourceFiles.isEmpty()) {
            throw new IllegalArgumentException("sourceFiles cannot be empty");
        }
        
        // Create a map with null ZIP paths to trigger default path generation
        Map<Path, String> fileToZipPathMap = new LinkedHashMap<>();
        for (Path sourceFile : sourceFiles) {
            fileToZipPathMap.put(sourceFile, null);
        }
        
        return compressFiles(fileToZipPathMap, outputZip);
    }
    
    /**
     * Compresses multiple files into a ZIP archive using multiple threads.
     * This method implements comprehensive error handling, resource cleanup, and graceful shutdown.
     *
     * @param fileToZipPathMap mapping of source file paths to ZIP internal paths (null values will use default paths)
     * @param outputZip the path where the ZIP file will be written
     * @return a CompressionResult containing compression statistics and metadata
     * @throws IOException if an I/O error occurs during compression or assembly
     * @throws MultiThreadCompressionException if multiple compression errors occur
     * @throws IllegalStateException if the compressor has been closed
     * @throws IllegalArgumentException if parameters are invalid
     */
    public CompressionResult compressFiles(Map<Path, String> fileToZipPathMap, Path outputZip) 
            throws IOException {
        
        if (closed) {
            throw new IllegalStateException("MultiThreadedZipCompressor has been closed");
        }
        
        Objects.requireNonNull(fileToZipPathMap, "fileToZipPathMap cannot be null");
        Objects.requireNonNull(outputZip, "outputZip cannot be null");
        
        if (fileToZipPathMap.isEmpty()) {
            throw new IllegalArgumentException("fileToZipPathMap cannot be empty");
        }
        
        // Process file mappings with default path generation and validation
        Map<Path, String> processedFileMap = processFilePathMappings(fileToZipPathMap);
        
        // Validate input files and paths
        validateInputs(processedFileMap);
        
        long startTime = System.nanoTime();
        List<CompletableFuture<SingleFileCompressionResult>> futures = null;
        boolean partialOutputCreated = false;
        
        try {
            // Create compression tasks
            List<CompressionTask> tasks = createCompressionTasks(processedFileMap);
            
            // Determine effective thread count (don't use more threads than files)
            int effectiveThreadCount = Math.min(threadCount, tasks.size());
            
            // Submit tasks and collect futures
            futures = submitCompressionTasks(tasks);
            
            // Wait for all tasks to complete and collect results
            List<SingleFileCompressionResult> results = collectResults(futures);
            
            // Mark that we're about to create output (for cleanup purposes)
            partialOutputCreated = true;
            
            // Assemble the final ZIP file
            zipAssembler.assembleZip(results, outputZip);
            
            // Calculate compression statistics
            Duration compressionTime = Duration.ofNanos(System.nanoTime() - startTime);
            long originalSize = results.stream().mapToLong(SingleFileCompressionResult::getUncompressedSize).sum();
            long compressedSize = results.stream().mapToLong(SingleFileCompressionResult::getCompressedSize).sum();
            
            return new CompressionResult(
                originalSize,
                compressedSize,
                results.size(),
                compressionTime,
                null,  // no errors
                true,  // successful
                effectiveThreadCount
            );
            
        } catch (MultiThreadCompressionException e) {
            // Handle multi-thread compression exceptions with detailed cleanup
            performErrorCleanup(futures, outputZip, partialOutputCreated);
            
            // Add timing information to the exception
            Duration compressionTime = Duration.ofNanos(System.nanoTime() - startTime);
            System.err.println("Multi-threaded compression failed after " + compressionTime);
            
            // Create detailed error messages for CompressionResult
            List<String> errorMessages = new ArrayList<>();
            for (CompressionException ce : e.getExceptions()) {
                errorMessages.add(ce.getMessage());
            }
            
            // Calculate original size from input files for error reporting
            long originalSize = calculateOriginalSize(fileToZipPathMap);
            
            // Create a failed result with detailed error information
            CompressionResult failedResult = CompressionResult.createFailedResult(
                originalSize, compressionTime, errorMessages, Math.min(threadCount, fileToZipPathMap.size()));
            
            // Log detailed summary
            System.err.println(failedResult.getDetailedSummary());
            System.err.println(e.getErrorSummary());
            
            // Re-throw with additional context
            throw e;
                
        } catch (IOException e) {
            // Handle I/O errors with cleanup
            performErrorCleanup(futures, outputZip, partialOutputCreated);
            
            Duration compressionTime = Duration.ofNanos(System.nanoTime() - startTime);
            throw new CompressionException(
                "I/O error during compression after " + compressionTime, e,
                null, CompressionException.CompressionStage.WRITING_TO_ZIP);
                
        } catch (Exception e) {
            // Handle any other unexpected exceptions with cleanup
            performErrorCleanup(futures, outputZip, partialOutputCreated);
            
            Duration compressionTime = Duration.ofNanos(System.nanoTime() - startTime);
            throw new CompressionException(
                "Unexpected error during compression after " + compressionTime, e,
                null, CompressionException.CompressionStage.FINALIZATION);
        }
    }
    
    /**
     * Performs comprehensive cleanup when errors occur during compression.
     * This includes cancelling pending tasks, cleaning up partial output files,
     * and logging cleanup actions.
     *
     * @param futures the list of compression task futures (may be null)
     * @param outputZip the output ZIP file path
     * @param partialOutputCreated whether partial output was created
     */
    private void performErrorCleanup(List<CompletableFuture<SingleFileCompressionResult>> futures,
                                   Path outputZip, boolean partialOutputCreated) {
        
        System.err.println("Performing error cleanup for failed compression operation");
        
        // Cancel any pending compression tasks
        if (futures != null) {
            int cancelledTasks = 0;
            for (CompletableFuture<SingleFileCompressionResult> future : futures) {
                if (!future.isDone() && future.cancel(true)) {
                    cancelledTasks++;
                }
            }
            if (cancelledTasks > 0) {
                System.err.println("Cancelled " + cancelledTasks + " pending compression tasks");
            }
        }
        
        // Clean up partial output file if it was created
        if (partialOutputCreated && outputZip != null) {
            try {
                if (Files.exists(outputZip)) {
                    Files.delete(outputZip);
                    System.err.println("Cleaned up partial output file: " + outputZip);
                }
            } catch (IOException e) {
                System.err.println("Warning: Failed to clean up partial output file " + outputZip + ": " + e.getMessage());
            }
        }
        
        // Force garbage collection to free up any resources held by failed tasks
        System.gc();
    }
    
    /**
     * Processes file path mappings by generating default ZIP paths for null values
     * and validating the directory structure requirements.
     * 
     * @param fileToZipPathMap the original file mappings (may contain null ZIP paths)
     * @return a new map with all ZIP paths populated (no null values)
     * @throws IllegalArgumentException if path processing fails
     * @throws IOException if file access fails during path generation
     */
    private Map<Path, String> processFilePathMappings(Map<Path, String> fileToZipPathMap) 
            throws IOException {
        
        Map<Path, String> processedMap = new LinkedHashMap<>();
        Set<String> usedZipPaths = new HashSet<>();
        
        // First pass: collect all explicitly specified ZIP paths
        for (Map.Entry<Path, String> entry : fileToZipPathMap.entrySet()) {
            String zipPath = entry.getValue();
            if (zipPath != null && !zipPath.trim().isEmpty()) {
                String normalizedPath = normalizeZipPath(zipPath.trim());
                if (usedZipPaths.contains(normalizedPath)) {
                    throw new IllegalArgumentException("Duplicate ZIP path specified: " + normalizedPath);
                }
                usedZipPaths.add(normalizedPath);
            }
        }
        
        // Second pass: generate default paths for null values and validate all paths
        for (Map.Entry<Path, String> entry : fileToZipPathMap.entrySet()) {
            Path sourceFile = entry.getKey();
            String zipPath = entry.getValue();
            
            if (zipPath == null) {
                // Generate default ZIP path using source file's relative path
                zipPath = generateDefaultZipPath(sourceFile, usedZipPaths);
                usedZipPaths.add(zipPath);
            } else {
                // Validate the original path before normalization
                validateRawZipPath(zipPath.trim(), sourceFile);
                zipPath = normalizeZipPath(zipPath.trim());
            }
            
            // Validate the final ZIP path
            validateZipPath(zipPath, sourceFile);
            
            processedMap.put(sourceFile, zipPath);
        }
        
        // Validate directory structure requirements
        validateDirectoryStructure(processedMap);
        
        return processedMap;
    }
    
    /**
     * Generates a default ZIP path for a source file, ensuring uniqueness.
     * 
     * @param sourceFile the source file path
     * @param usedPaths set of already used ZIP paths
     * @return a unique default ZIP path
     * @throws IOException if file access fails
     */
    private String generateDefaultZipPath(Path sourceFile, Set<String> usedPaths) throws IOException {
        // Use the file name as the default ZIP path
        String fileName = sourceFile.getFileName().toString();
        String basePath = fileName;
        
        // If the base path is already used, add a counter
        if (usedPaths.contains(basePath)) {
            int counter = 1;
            String extension = "";
            String nameWithoutExtension = fileName;
            
            // Extract extension if present
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < fileName.length() - 1) {
                nameWithoutExtension = fileName.substring(0, lastDot);
                extension = fileName.substring(lastDot);
            }
            
            // Find a unique name
            do {
                basePath = nameWithoutExtension + "_" + counter + extension;
                counter++;
            } while (usedPaths.contains(basePath));
        }
        
        return basePath;
    }
    
    /**
     * Normalizes a ZIP path by ensuring proper format (assumes path is already validated).
     * 
     * @param zipPath the ZIP path to normalize
     * @return the normalized ZIP path
     * @throws IllegalArgumentException if the path cannot be normalized
     */
    private String normalizeZipPath(String zipPath) {
        if (zipPath == null || zipPath.trim().isEmpty()) {
            throw new IllegalArgumentException("ZIP path cannot be null or empty");
        }
        
        String normalized = zipPath.trim();
        
        // Remove leading slashes
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        
        // Remove trailing slashes (files shouldn't end with /)
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        // Validate that we still have a path after normalization
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ZIP path becomes empty after normalization: " + zipPath);
        }
        
        return normalized;
    }
    
    /**
     * Validates a raw ZIP path before normalization to catch format issues.
     * 
     * @param zipPath the raw ZIP path to validate
     * @param sourceFile the source file (for error reporting)
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRawZipPath(String zipPath, Path sourceFile) {
        // Check for null or empty
        if (zipPath == null || zipPath.trim().isEmpty()) {
            throw new IllegalArgumentException("ZIP path cannot be null or empty for file: " + sourceFile);
        }
        
        // Check for invalid characters (before normalization)
        if (zipPath.contains("\\")) {
            throw new IllegalArgumentException("ZIP path should use forward slashes, not backslashes: " + zipPath);
        }
        
        // Check for path traversal attempts
        if (zipPath.contains("../") || zipPath.contains("..\\") || zipPath.equals("..")) {
            throw new IllegalArgumentException("ZIP path cannot contain parent directory references (..): " + zipPath);
        }
        
        // Check for absolute paths
        if (zipPath.startsWith("/")) {
            throw new IllegalArgumentException("ZIP path cannot be absolute (start with /): " + zipPath);
        }
    }
    
    /**
     * Validates a single ZIP path for format compliance and security.
     * 
     * @param zipPath the ZIP path to validate (should be normalized)
     * @param sourceFile the source file (for error reporting)
     * @throws IllegalArgumentException if validation fails
     */
    private void validateZipPath(String zipPath, Path sourceFile) {
        // Check for null or empty
        if (zipPath == null || zipPath.trim().isEmpty()) {
            throw new IllegalArgumentException("ZIP path cannot be null or empty for file: " + sourceFile);
        }
        
        // Check for path traversal attempts
        if (zipPath.contains("../") || zipPath.contains("..\\") || zipPath.equals("..")) {
            throw new IllegalArgumentException("ZIP path cannot contain parent directory references (..): " + zipPath);
        }
        
        // Check for absolute paths (should not happen after normalization, but check anyway)
        if (zipPath.startsWith("/")) {
            throw new IllegalArgumentException("ZIP path cannot be absolute (start with /): " + zipPath);
        }
        
        // Check for reserved names (Windows)
        String[] pathParts = zipPath.split("/");
        for (String part : pathParts) {
            if (isReservedName(part)) {
                throw new IllegalArgumentException("ZIP path contains reserved name: " + part + " in " + zipPath);
            }
        }
        
        // Check for excessively long paths
        if (zipPath.length() > 260) {
            throw new IllegalArgumentException("ZIP path is too long (max 260 characters): " + zipPath);
        }
        
        // Check for empty path components
        if (zipPath.contains("//")) {
            throw new IllegalArgumentException("ZIP path cannot contain empty path components (//): " + zipPath);
        }
    }
    
    /**
     * Checks if a filename is a reserved name on Windows systems.
     * 
     * @param name the filename to check
     * @return true if the name is reserved
     */
    private boolean isReservedName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        // Remove extension for checking
        String baseName = name.toUpperCase();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        
        // Windows reserved names
        String[] reservedNames = {
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        };
        
        for (String reserved : reservedNames) {
            if (reserved.equals(baseName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Validates that the directory structure implied by ZIP paths is valid.
     * This ensures that directory entries are properly handled and there are no conflicts
     * between file and directory paths.
     * 
     * @param fileToZipPathMap the processed file mappings
     * @throws IllegalArgumentException if directory structure validation fails
     */
    private void validateDirectoryStructure(Map<Path, String> fileToZipPathMap) {
        Set<String> filePaths = new HashSet<>();
        Set<String> impliedDirectories = new HashSet<>();
        
        // Collect all file paths and implied directory paths
        for (String zipPath : fileToZipPathMap.values()) {
            filePaths.add(zipPath);
            
            // Add all parent directories to the implied directories set
            String[] pathParts = zipPath.split("/");
            StringBuilder currentPath = new StringBuilder();
            
            for (int i = 0; i < pathParts.length - 1; i++) {
                if (currentPath.length() > 0) {
                    currentPath.append("/");
                }
                currentPath.append(pathParts[i]);
                impliedDirectories.add(currentPath.toString());
            }
        }
        
        // Check for conflicts between file paths and directory paths
        for (String filePath : filePaths) {
            if (impliedDirectories.contains(filePath)) {
                throw new IllegalArgumentException(
                    "Path conflict: '" + filePath + "' is used both as a file and as a directory");
            }
        }
        
        // Validate that no file path is a prefix of another (which would create conflicts)
        List<String> sortedPaths = new ArrayList<>(filePaths);
        sortedPaths.sort(String::compareTo);
        
        for (int i = 0; i < sortedPaths.size() - 1; i++) {
            String currentPath = sortedPaths.get(i);
            String nextPath = sortedPaths.get(i + 1);
            
            if (nextPath.startsWith(currentPath + "/")) {
                throw new IllegalArgumentException(
                    "Path conflict: '" + currentPath + "' conflicts with '" + nextPath + 
                    "' (file cannot be parent of another file)");
            }
        }
    }
    
    /**
     * Validates the input file mappings.
     *
     * @param fileToZipPathMap the file mappings to validate
     * @throws IllegalArgumentException if validation fails
     * @throws IOException if file access fails
     */
    private void validateInputs(Map<Path, String> fileToZipPathMap) throws IOException {
        Set<String> zipPaths = new HashSet<>();
        
        for (Map.Entry<Path, String> entry : fileToZipPathMap.entrySet()) {
            Path sourceFile = entry.getKey();
            String zipPath = entry.getValue();
            
            // Validate source file
            if (sourceFile == null) {
                throw new IllegalArgumentException("Source file path cannot be null");
            }
            
            if (!Files.exists(sourceFile)) {
                throw new IllegalArgumentException("Source file does not exist: " + sourceFile);
            }
            
            if (!Files.isRegularFile(sourceFile)) {
                throw new IllegalArgumentException("Source path is not a regular file: " + sourceFile);
            }
            
            if (!Files.isReadable(sourceFile)) {
                throw new IllegalArgumentException("Source file is not readable: " + sourceFile);
            }
            
            // Validate ZIP path
            if (zipPath == null || zipPath.trim().isEmpty()) {
                throw new IllegalArgumentException("ZIP path cannot be null or empty for file: " + sourceFile);
            }
            
            // Check for duplicate ZIP paths
            if (!zipPaths.add(zipPath)) {
                throw new IllegalArgumentException("Duplicate ZIP path: " + zipPath);
            }
            
            // Validate ZIP path format (basic validation)
            if (zipPath.contains("\\")) {
                throw new IllegalArgumentException("ZIP path should use forward slashes: " + zipPath);
            }
        }
    }
    
    /**
     * Creates compression tasks for all files with calculated offsets.
     *
     * @param fileToZipPathMap the file mappings
     * @return a list of compression tasks
     * @throws IOException if file size calculation fails
     */
    private List<CompressionTask> createCompressionTasks(Map<Path, String> fileToZipPathMap) 
            throws IOException {
        
        List<CompressionTask> tasks = new ArrayList<>();
        long currentOffset = 0;
        
        // Create tasks in a consistent order (sorted by ZIP path for reproducibility)
        List<Map.Entry<Path, String>> sortedEntries = fileToZipPathMap.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toList());
        
        for (Map.Entry<Path, String> entry : sortedEntries) {
            Path sourceFile = entry.getKey();
            String zipPath = entry.getValue();
            
            long fileSize = Files.size(sourceFile);
            
            CompressionTask task = new CompressionTask(sourceFile, zipPath, fileSize, currentOffset);
            tasks.add(task);
            
            // Estimate the space this entry will take (header + compressed data)
            // This is an approximation; actual offsets will be recalculated during assembly
            currentOffset += estimateEntrySize(zipPath, fileSize);
        }
        
        return tasks;
    }
    
    /**
     * Estimates the size an entry will take in the ZIP file.
     * This is used for initial offset calculation.
     *
     * @param zipPath the ZIP path of the entry
     * @param fileSize the uncompressed file size
     * @return estimated size in bytes
     */
    private long estimateEntrySize(String zipPath, long fileSize) {
        // Local header size: 30 bytes + filename length
        long headerSize = 30 + zipPath.getBytes().length;
        
        // Estimate compressed size (assume 50% compression ratio as rough estimate)
        long estimatedCompressedSize = Math.max(fileSize / 2, fileSize / 10);
        
        return headerSize + estimatedCompressedSize;
    }
    
    /**
     * Submits compression tasks to the executor service.
     *
     * @param tasks the compression tasks to submit
     * @return a list of CompletableFuture objects representing the submitted tasks
     */
    private List<CompletableFuture<SingleFileCompressionResult>> submitCompressionTasks(
            List<CompressionTask> tasks) {
        
        List<CompletableFuture<SingleFileCompressionResult>> futures = new ArrayList<>();
        
        for (CompressionTask task : tasks) {
            CompletableFuture<SingleFileCompressionResult> future = 
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new CompressionException("Task execution failed", e);
                    }
                }, executorService);
            
            futures.add(future);
        }
        
        return futures;
    }
    
    /**
     * Collects results from all compression tasks, handling errors appropriately.
     * This method implements comprehensive error collection and partial failure handling.
     *
     * @param futures the list of futures representing compression tasks
     * @return a list of successful compression results
     * @throws MultiThreadCompressionException if any tasks failed
     */
    private List<SingleFileCompressionResult> collectResults(
            List<CompletableFuture<SingleFileCompressionResult>> futures) {
        
        List<SingleFileCompressionResult> results = new ArrayList<>();
        List<CompressionException> errors = new ArrayList<>();
        List<String> successfulFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        
        // Track completion status for detailed error reporting
        int completedTasks = 0;
        int totalTasks = futures.size();
        
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<SingleFileCompressionResult> future = futures.get(i);
            try {
                SingleFileCompressionResult result = future.get();
                results.add(result);
                successfulFiles.add(result.getEntryName());
                completedTasks++;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                CompressionException compressionError;
                
                if (cause instanceof CompressionException) {
                    compressionError = (CompressionException) cause;
                } else {
                    compressionError = new CompressionException(
                        "Unexpected error during compression", cause, 
                        "task-" + i, CompressionException.CompressionStage.COMPRESSION);
                }
                
                errors.add(compressionError);
                if (compressionError.getEntryName() != null) {
                    failedFiles.add(compressionError.getEntryName());
                } else {
                    failedFiles.add("unknown-file-" + i);
                }
                
                // Log individual error for debugging
                System.err.println("Compression task failed: " + compressionError.getMessage());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CompressionException interruptError = new CompressionException(
                    "Compression was interrupted", e, 
                    "task-" + i, CompressionException.CompressionStage.COMPRESSION);
                errors.add(interruptError);
                failedFiles.add("interrupted-task-" + i);
                
                // If interrupted, cancel remaining tasks for faster shutdown
                cancelRemainingTasks(futures, i + 1);
                break;
            } catch (Exception e) {
                // Handle any other unexpected exceptions
                CompressionException unexpectedError = new CompressionException(
                    "Unexpected error collecting compression result", e,
                    "task-" + i, CompressionException.CompressionStage.FINALIZATION);
                errors.add(unexpectedError);
                failedFiles.add("error-task-" + i);
            }
        }
        
        // If there were any errors, perform cleanup and throw detailed exception
        if (!errors.isEmpty()) {
            // Log summary of partial failure
            System.err.printf("Compression completed with partial failures: %d/%d tasks succeeded%n", 
                            completedTasks, totalTasks);
            System.err.println("Successful files: " + successfulFiles);
            System.err.println("Failed files: " + failedFiles);
            
            // Create enhanced multi-thread exception with detailed information
            MultiThreadCompressionException multiError = new MultiThreadCompressionException(errors);
            
            // Add suppressed exceptions for additional context
            for (CompressionException error : errors) {
                if (error.getCause() != null) {
                    multiError.addSuppressed(error.getCause());
                }
            }
            
            throw multiError;
        }
        
        // Sort results by ZIP path to ensure consistent ordering
        results.sort(Comparator.comparing(SingleFileCompressionResult::getEntryName));
        
        // Recalculate offsets based on actual sizes
        return zipAssembler.recalculateOffsets(results);
    }
    
    /**
     * Cancels remaining tasks when an interruption occurs to enable faster shutdown.
     *
     * @param futures the list of all futures
     * @param startIndex the index to start cancelling from
     */
    private void cancelRemainingTasks(List<CompletableFuture<SingleFileCompressionResult>> futures, 
                                    int startIndex) {
        for (int i = startIndex; i < futures.size(); i++) {
            CompletableFuture<SingleFileCompressionResult> future = futures.get(i);
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }
    
    /**
     * Calculates the total original size of all input files.
     * This is used for error reporting when compression fails.
     *
     * @param fileToZipPathMap the file mappings
     * @return the total size of all input files in bytes
     */
    private long calculateOriginalSize(Map<Path, String> fileToZipPathMap) {
        long totalSize = 0;
        for (Path sourceFile : fileToZipPathMap.keySet()) {
            try {
                totalSize += Files.size(sourceFile);
            } catch (IOException e) {
                // If we can't read the file size, just continue
                // This is for error reporting, so we don't want to throw here
                System.err.println("Warning: Could not read size of file " + sourceFile + " for error reporting");
            }
        }
        return totalSize;
    }
    
    /**
     * @return the number of threads configured for this compressor
     */
    public int getThreadCount() {
        return threadCount;
    }
    
    /**
     * @return true if this compressor has been closed
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * Closes the compressor and shuts down the thread pool gracefully.
     * This method implements comprehensive shutdown logic with error handling and resource cleanup.
     * After calling this method, the compressor cannot be used for further compression operations.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        
        closed = true;
        
        // Shutdown the executor service gracefully
        executorService.shutdown();
        
        try {
            // Wait for existing tasks to complete with a reasonable timeout
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                System.err.println("Thread pool shutdown timeout reached, forcing shutdown...");
                
                // Get list of tasks that never started
                List<Runnable> pendingTasks = executorService.shutdownNow();
                if (!pendingTasks.isEmpty()) {
                    System.err.println("Cancelled " + pendingTasks.size() + " pending tasks during shutdown");
                }
                
                // Wait a bit more for tasks to respond to being cancelled
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("Warning: Thread pool did not terminate gracefully within timeout");
                    
                    // Log active thread information for debugging
                    if (executorService instanceof ThreadPoolExecutor) {
                        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
                        System.err.println("Active threads: " + tpe.getActiveCount());
                        System.err.println("Pool size: " + tpe.getPoolSize());
                        System.err.println("Task count: " + tpe.getTaskCount());
                        System.err.println("Completed task count: " + tpe.getCompletedTaskCount());
                    }
                } else {
                    System.err.println("Thread pool shutdown completed after forced termination");
                }
            } else {
                System.out.println("Thread pool shutdown completed gracefully");
            }
            
        } catch (InterruptedException e) {
            System.err.println("Thread pool shutdown was interrupted, forcing immediate shutdown");
            
            // Re-interrupt the current thread to preserve interrupt status
            Thread.currentThread().interrupt();
            
            // Force immediate shutdown
            List<Runnable> pendingTasks = executorService.shutdownNow();
            if (!pendingTasks.isEmpty()) {
                System.err.println("Cancelled " + pendingTasks.size() + " pending tasks during interrupted shutdown");
            }
            
            // Try one more time to wait for termination, but with a very short timeout
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("Warning: Thread pool may not have terminated completely after interrupt");
                }
            } catch (InterruptedException ie) {
                // If interrupted again, just log and continue
                System.err.println("Multiple interrupts during shutdown, proceeding with cleanup");
            }
        }
        
        // Perform final cleanup
        performShutdownCleanup();
    }
    
    /**
     * Performs final cleanup operations during shutdown.
     * This method handles any remaining resource cleanup that needs to be done
     * when the compressor is being closed.
     */
    private void performShutdownCleanup() {
        try {
            // Force garbage collection to help free up resources
            System.gc();
            
            // Log successful shutdown
            System.out.println("MultiThreadedZipCompressor shutdown completed");
            
        } catch (Exception e) {
            // Log any unexpected errors during cleanup, but don't throw
            System.err.println("Warning: Error during shutdown cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Initiates an emergency shutdown of the compressor.
     * This method is called when critical errors occur and immediate shutdown is required.
     * It attempts to cancel all running tasks and shut down the thread pool as quickly as possible.
     */
    public void emergencyShutdown() {
        if (closed) {
            return;
        }
        
        System.err.println("Initiating emergency shutdown of MultiThreadedZipCompressor");
        
        closed = true;
        
        // Immediately shutdown the executor service without waiting for tasks to complete
        List<Runnable> pendingTasks = executorService.shutdownNow();
        
        if (!pendingTasks.isEmpty()) {
            System.err.println("Emergency shutdown cancelled " + pendingTasks.size() + " pending tasks");
        }
        
        // Wait briefly for threads to respond to cancellation
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Warning: Some threads may not have terminated during emergency shutdown");
            } else {
                System.err.println("Emergency shutdown completed successfully");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Emergency shutdown was interrupted");
        }
        
        // Perform cleanup
        performShutdownCleanup();
    }
    
    @Override
    public String toString() {
        return String.format("MultiThreadedZipCompressor{threadCount=%d, closed=%s}", 
                           threadCount, closed);
    }
}