package com.github.rzymek.opczip.excel;

import com.github.rzymek.opczip.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Excel 파일을 멀티스레드로 압축하는 클래스입니다.
 * 각 시트를 별도의 스레드에서 XML로 변환하고 압축한 후,
 * 최종적으로 완전한 Excel 파일(.xlsx)로 조립합니다.
 */
public class MultiThreadedExcelCompressor implements AutoCloseable {

    private final int threadCount;
    private final ExecutorService executorService;
    private final ZipAssembler zipAssembler;
    private volatile boolean closed = false;

    /**
     * 기본 스레드 수(CPU 코어 수)로 압축기를 생성합니다.
     */
    public MultiThreadedExcelCompressor() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * 지정된 스레드 수로 압축기를 생성합니다.
     *
     * @param threadCount 사용할 스레드 수
     * @throws IllegalArgumentException 스레드 수가 1보다 작은 경우
     */
    public MultiThreadedExcelCompressor(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("Thread count must be at least 1");
        }

        this.threadCount = threadCount;
        this.executorService = createExecutorService(threadCount);
        this.zipAssembler = new ZipAssembler();
    }

    /**
     * Excel 워크북을 압축하여 .xlsx 파일로 저장합니다.
     *
     * @param workbook   압축할 Excel 워크북
     * @param outputPath 출력 파일 경로
     * @return 압축 결과
     * @throws IOException                     I/O 오류 발생시
     * @throws MultiThreadCompressionException 압축 오류 발생시
     */
    public CompressionResult compressWorkbook(ExcelWorkbook workbook, Path outputPath) throws IOException {
        if (closed) {
            throw new IllegalStateException("MultiThreadedExcelCompressor has been closed");
        }

        Objects.requireNonNull(workbook, "workbook cannot be null");
        Objects.requireNonNull(outputPath, "outputPath cannot be null");

        if (workbook.isEmpty()) {
            throw new IllegalArgumentException("Workbook cannot be empty");
        }

        long startTime = System.nanoTime();
        List<CompletableFuture<SingleFileCompressionResult>> futures = null;
        boolean partialOutputCreated = false;

        try {
            // 1. Excel 메타데이터 파일들 생성
            List<SingleFileCompressionResult> metadataResults = createExcelMetadataFiles(workbook);

            // 2. 시트 압축 작업 생성 및 실행
            List<ExcelSheetCompressionTask> sheetTasks = createSheetCompressionTasks(workbook);
            futures = submitSheetCompressionTasks(sheetTasks);

            // 3. 시트 압축 결과 수집
            List<SingleFileCompressionResult> sheetResults = collectSheetResults(futures);

            // 4. 모든 결과 합치기
            List<SingleFileCompressionResult> allResults = new ArrayList<>();
            allResults.addAll(metadataResults);
            allResults.addAll(sheetResults);

            // 5. 최종 Excel 파일 조립
            partialOutputCreated = true;
            zipAssembler.assembleZip(allResults, outputPath);

            // 6. 압축 통계 계산
            Duration compressionTime = Duration.ofNanos(System.nanoTime() - startTime);
            long originalSize = allResults.stream().mapToLong(SingleFileCompressionResult::getUncompressedSize).sum();
            long compressedSize = allResults.stream().mapToLong(SingleFileCompressionResult::getCompressedSize).sum();

            return new CompressionResult(
                    originalSize,
                    compressedSize,
                    allResults.size(),
                    compressionTime,
                    null,  // no errors
                    true,  // successful
                    Math.min(threadCount, workbook.getSheetCount())
            );

        } catch (MultiThreadCompressionException e) {
            performErrorCleanup(futures, outputPath, partialOutputCreated);
            Duration compressionTime = Duration.ofNanos(System.nanoTime() - startTime);
            System.err.println("Multi-threaded Excel compression failed after " + compressionTime);
            throw e;

        } catch (IOException e) {
            performErrorCleanup(futures, outputPath, partialOutputCreated);
            Duration compressionTime = Duration.ofNanos(System.nanoTime() - startTime);
            throw new CompressionException(
                    "I/O error during Excel compression after " + compressionTime, e,
                    null, CompressionException.CompressionStage.WRITING_TO_ZIP);

        } catch (Exception e) {
            performErrorCleanup(futures, outputPath, partialOutputCreated);
            Duration compressionTime = Duration.ofNanos(System.nanoTime() - startTime);
            throw new CompressionException(
                    "Unexpected error during Excel compression after " + compressionTime, e,
                    null, CompressionException.CompressionStage.FINALIZATION);
        }
    }

    /**
     * Excel 메타데이터 파일들을 생성합니다.
     *
     * @param workbook Excel 워크북
     * @return 메타데이터 파일 압축 결과 목록
     * @throws IOException 파일 생성 중 오류 발생시
     */
    private List<SingleFileCompressionResult> createExcelMetadataFiles(ExcelWorkbook workbook) throws IOException {
        List<SingleFileCompressionResult> results = new ArrayList<>();

        // 1. [Content_Types].xml
        byte[] contentTypesXml = ExcelMetadataGenerator.generateContentTypesXml(workbook);
        results.add(createMetadataResult("[Content_Types].xml", contentTypesXml));

        // 2. _rels/.rels
        byte[] mainRelsXml = ExcelMetadataGenerator.generateMainRelsXml();
        results.add(createMetadataResult("_rels/.rels", mainRelsXml));

        // 3. xl/workbook.xml
        byte[] workbookXml = ExcelXmlGenerator.generateWorkbookXml(workbook);
        results.add(createMetadataResult("xl/workbook.xml", workbookXml));

        // 4. xl/_rels/workbook.xml.rels
        byte[] workbookRelsXml = ExcelMetadataGenerator.generateWorkbookRelsXml(workbook);
        results.add(createMetadataResult("xl/_rels/workbook.xml.rels", workbookRelsXml));

        // 5. xl/styles.xml
        byte[] stylesXml = ExcelXmlGenerator.generateStylesXml();
        results.add(createMetadataResult("xl/styles.xml", stylesXml));

        // 6. xl/sharedStrings.xml (문자열이 있는 경우에만)
        if (hasStringValues(workbook)) {
            byte[] sharedStringsXml = ExcelXmlGenerator.generateSharedStringsXml(workbook);
            results.add(createMetadataResult("xl/sharedStrings.xml", sharedStringsXml));
        }

        return results;
    }

    /**
     * 메타데이터 파일의 압축 결과를 생성합니다.
     *
     * @param fileName 파일 이름
     * @param data     파일 데이터
     * @return 압축 결과
     * @throws IOException 압축 중 오류 발생시
     */
    private SingleFileCompressionResult createMetadataResult(String fileName, byte[] data) throws IOException {
        long startTime = System.nanoTime();
        
        // 메타데이터 파일은 압축하지 않고 저장 (STORE method)
        // 작은 XML 파일들은 압축 효과가 적고 STORE가 더 안정적임
        long crc32 = calculateCrc32(data);
        long dosTime = toDosTime(java.time.LocalDateTime.now());
        long compressionTimeNanos = System.nanoTime() - startTime;

        return new SingleFileCompressionResult(
                fileName,
                null,  // ZipAssembler가 로컬 헤더를 생성함
                data,  // 압축하지 않음 (STORE method)
                crc32,
                data.length,
                data.length,  // 압축하지 않으므로 같은 크기
                dosTime,
                0,  // ZipAssembler가 오프셋을 설정함
                Duration.ofNanos(compressionTimeNanos)
        );
    }

    /**
     * 시트 압축 작업들을 생성합니다.
     *
     * @param workbook Excel 워크북
     * @return 시트 압축 작업 목록
     */
    private List<ExcelSheetCompressionTask> createSheetCompressionTasks(ExcelWorkbook workbook) {
        List<ExcelSheetCompressionTask> tasks = new ArrayList<>();
        List<ExcelSheet> sheets = workbook.getSheets();

        for (int i = 0; i < sheets.size(); i++) {
            ExcelSheet sheet = sheets.get(i);
            String zipPath = "xl/worksheets/sheet" + (i + 1) + ".xml";

            ExcelSheetCompressionTask task = new ExcelSheetCompressionTask(
                    sheet, zipPath, 0, i + 1);
            tasks.add(task);
        }

        return tasks;
    }

    /**
     * 시트 압축 작업들을 실행합니다.
     *
     * @param tasks 압축 작업 목록
     * @return CompletableFuture 목록
     */
    private List<CompletableFuture<SingleFileCompressionResult>> submitSheetCompressionTasks(
            List<ExcelSheetCompressionTask> tasks) {

        List<CompletableFuture<SingleFileCompressionResult>> futures = new ArrayList<>();

        for (ExcelSheetCompressionTask task : tasks) {
            CompletableFuture<SingleFileCompressionResult> future =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return task.call();
                        } catch (Exception e) {
                            if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                            }
                            throw new CompressionException("Sheet compression task failed", e);
                        }
                    }, executorService);

            futures.add(future);
        }

        return futures;
    }

    /**
     * 시트 압축 결과를 수집합니다.
     *
     * @param futures CompletableFuture 목록
     * @return 압축 결과 목록
     * @throws MultiThreadCompressionException 압축 오류 발생시
     */
    private List<SingleFileCompressionResult> collectSheetResults(
            List<CompletableFuture<SingleFileCompressionResult>> futures) {

        List<SingleFileCompressionResult> results = new ArrayList<>();
        List<CompressionException> errors = new ArrayList<>();

        for (CompletableFuture<SingleFileCompressionResult> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof CompressionException) {
                    errors.add((CompressionException) cause);
                } else {
                    errors.add(new CompressionException("Sheet compression failed", cause));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errors.add(new CompressionException("Sheet compression interrupted", e));
            }
        }

        if (!errors.isEmpty()) {
            throw new MultiThreadCompressionException(errors);
        }

        return results;
    }

    /**
     * 워크북에 문자열 값이 있는지 확인합니다.
     *
     * @param workbook Excel 워크북
     * @return 문자열 값이 있으면 true
     */
    private boolean hasStringValues(ExcelWorkbook workbook) {
        for (ExcelSheet sheet : workbook.getSheets()) {
            for (Object value : sheet.getCells().values()) {
                if (value instanceof String) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 오류 발생시 정리 작업을 수행합니다.
     *
     * @param futures              CompletableFuture 목록
     * @param outputPath           출력 파일 경로
     * @param partialOutputCreated 부분 출력 파일 생성 여부
     */
    private void performErrorCleanup(List<CompletableFuture<SingleFileCompressionResult>> futures,
                                     Path outputPath, boolean partialOutputCreated) {

        System.err.println("Performing error cleanup for failed Excel compression operation");

        // 진행 중인 작업 취소
        if (futures != null) {
            int cancelledTasks = 0;
            for (CompletableFuture<SingleFileCompressionResult> future : futures) {
                if (!future.isDone() && future.cancel(true)) {
                    cancelledTasks++;
                }
            }
            if (cancelledTasks > 0) {
                System.err.println("Cancelled " + cancelledTasks + " pending sheet compression tasks");
            }
        }

        // 부분 출력 파일 정리
        if (partialOutputCreated && outputPath != null) {
            try {
                if (Files.exists(outputPath)) {
                    Files.delete(outputPath);
                    System.err.println("Cleaned up partial Excel output file: " + outputPath);
                }
            } catch (IOException e) {
                System.err.println("Warning: Failed to clean up partial Excel output file " + outputPath + ": " + e.getMessage());
            }
        }

        System.gc();
    }

    /**
     * ExecutorService를 생성합니다.
     *
     * @param threadCount 스레드 수
     * @return ExecutorService
     */
    private ExecutorService createExecutorService(int threadCount) {
        return new ThreadPoolExecutor(
                threadCount,
                threadCount,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private int counter = 0;

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "ExcelCompressor-" + (++counter));
                        t.setDaemon(false);
                        return t;
                    }
                }
        );
    }

    /**
     * CRC32를 계산합니다.
     *
     * @param data 데이터
     * @return CRC32 값
     */
    private long calculateCrc32(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }

    /**
     * Java LocalDateTime을 MS-DOS 시간/날짜 형식으로 변환합니다.
     *
     * @param time 변환할 시간
     * @return DOS 시간 형식
     */
    private long toDosTime(java.time.LocalDateTime time) {
        int year = time.getYear();
        if (year < 1980) year = 1980;

        return ((long) (year - 1980) << 25) |
                ((long) time.getMonthValue() << 21) |
                ((long) time.getDayOfMonth() << 16) |
                ((long) time.getHour() << 11) |
                ((long) time.getMinute() << 5) |
                ((long) time.getSecond() >> 1);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}