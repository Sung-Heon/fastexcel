package com.github.rzymek.opczip;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * MultiThreadedZipCompressor 사용법과 성능 비교를 보여주는 예제 클래스입니다.
 */
public class MultiThreadedZipCompressorExample {

    public static void main(String[] args) {
        // 결과를 확인하기 쉽도록 프로젝트 루트에 'zip-output' 디렉터리를 생성합니다.
        Path outputDir = Paths.get("zip-output");
        Path multiThreadZip = outputDir.resolve("multi_thread_archive.zip");
        Path singleThreadZip = outputDir.resolve("single_thread_archive.zip");

        // 1. 테스트용 디렉터리와 다수의 파일 생성
        try {
            Files.createDirectories(outputDir);
            System.out.println("출력 디렉터리: " + outputDir.toAbsolutePath());

            List<Path> sourceFiles = createTestFiles(outputDir, 20);

            // 2. 다중 스레드 압축 실행 및 시간 측정
            runMultiThreadedCompression(sourceFiles, multiThreadZip);

            // 3. 단일 스레드 압축 실행 및 시간 측정
            runSingleThreadedCompression(sourceFiles, singleThreadZip);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 4. 생성된 파일 확인 안내
            System.out.println("\n--- 작업 완료 ---");
            System.out.println("생성된 파일은 다음 경로에서 확인하실 수 있습니다:");
            System.out.println(outputDir.toAbsolutePath());
            System.out.println("정리하려면 'zip-output' 디렉터리를 직접 삭제해주세요.");
        }
    }

    /**
     * 테스트를 위한 여러 개의 파일을 생성합니다.
     * @param dir 파일을 생성할 디렉터리
     * @param numFiles 생성할 파일 개수
     * @return 생성된 파일 경로 리스트
     * @throws IOException 파일 생성 중 오류 발생 시
     */
    private static List<Path> createTestFiles(Path dir, int numFiles) throws IOException {
        System.out.println("\n--- 테스트 파일 생성 시작 ---");
        List<Path> files = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < numFiles; i++) {
            Path file = dir.resolve("test_file_" + i + ".dat");
            // 1KB ~ 1MB 크기의 랜덤 데이터 생성
            int size = 1024 + random.nextInt(1024 * 1024);
            byte[] dummyData = new byte[size];
            random.nextBytes(dummyData);
            Files.write(file, dummyData);
            files.add(file);
        }
        System.out.println(numFiles + "개의 테스트 파일 생성 완료.");
        return files;
    }

    /**
     * MultiThreadedZipCompressor를 사용한 압축을 실행합니다.
     */
    private static void runMultiThreadedCompression(List<Path> sourceFiles, Path outputZip) throws IOException {
        System.out.println("\n--- 예제 1: 다중 스레드 압축 시작 ---");
        System.out.println("출력 파일: " + outputZip.toAbsolutePath());

        long startTime = System.nanoTime();

        // try-with-resources 구문을 사용하여 Compressor가 자동으로 닫히도록 합니다.
        try (MultiThreadedZipCompressor compressor = new MultiThreadedZipCompressor()) {
            // compressFiles 메서드 호출
            CompressionResult result = compressor.compressFiles(sourceFiles, outputZip);

            // 결과 출력
            System.out.println("다중 스레드 압축 성공!");
            System.out.println(result.getDetailedSummary());
        }

        long endTime = System.nanoTime();
        long durationMillis = (endTime - startTime) / 1_000_000;

        System.out.println("소요 시간: " + durationMillis + " ms");
    }

    /**
     * 전통적인 단일 스레드 방식으로 압축을 실행합니다.
     */
    private static void runSingleThreadedCompression(List<Path> sourceFiles, Path outputZip) throws IOException {
        System.out.println("\n--- 예제 2: 단일 스레드 압축 시작 ---");
        System.out.println("출력 파일: " + outputZip.toAbsolutePath());

        long startTime = System.nanoTime();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputZip)))) {
            for (Path sourceFile : sourceFiles) {
                ZipEntry zipEntry = new ZipEntry(sourceFile.getFileName().toString());
                zos.putNextEntry(zipEntry);
                Files.copy(sourceFile, zos);
                zos.closeEntry();
            }
        }

        long endTime = System.nanoTime();
        long durationMillis = (endTime - startTime) / 1_000_000;

        System.out.println("단일 스레드 압축 성공!");
        System.out.println("소요 시간: " + durationMillis + " ms");
        System.out.println("압축된 파일 수: " + sourceFiles.size());
        System.out.println("최종 ZIP 파일 크기: " + Files.size(outputZip) / 1024 + " KB");
    }
}
