package com.github.rzymek.opczip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MultiThreadedZipCompressor 사용법을 보여주는 예제 클래스입니다.
 */
public class MultiThreadedZipCompressorExample {

    public static void main(String[] args) {
        // 결과를 확인하기 쉽도록 프로젝트 루트에 'zip-output' 디렉터리를 생성합니다.
        Path outputDir = Paths.get("zip-output");
        Path outputZipSimple = null;
        Path outputZipMapped = null;

        // 1. 테스트용 디렉터리와 파일 생성
        try {
            Files.createDirectories(outputDir);
            System.out.println("출력 디렉터리: " + outputDir.toAbsolutePath());

            Path file1 = outputDir.resolve("document.txt");
            Files.write(file1, "This is a sample document for compression.".getBytes());

            Path file2 = outputDir.resolve("archive.dat");
            Files.write(file2, new byte[1024 * 512]); // 512KB dummy data

            Path subDir = Files.createDirectories(outputDir.resolve("data"));
            Path file3 = subDir.resolve("report.csv");
            Files.write(file3, "ID,Name,Score\n1,Test,100".getBytes());

            System.out.println("\n테스트 파일 생성 완료:");
            System.out.println("- " + file1.toAbsolutePath());
            System.out.println("- " + file2.toAbsolutePath());
            System.out.println("- " + file3.toAbsolutePath());

            List<Path> sourceFiles = Arrays.asList(file1, file2, file3);
            outputZipSimple = outputDir.resolve("simple_archive.zip");
            outputZipMapped = outputDir.resolve("mapped_archive.zip");

            // 2. 기본 사용법: 파일명으로 자동 압축
            runSimpleCompression(sourceFiles, outputZipSimple);

            // 3. 고급 사용법: ZIP 내부 경로를 직접 지정하여 압축
            runMappedCompression(file1, file2, file3, outputZipMapped);

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
     * 가장 기본적인 압축 방법을 보여줍니다.
     * 소스 파일의 파일명을 ZIP 엔트리 이름으로 자동 사용합니다.
     */
    private static void runSimpleCompression(List<Path> sourceFiles, Path outputZip) throws IOException {
        System.out.println("\n--- 예제 1: 기본 압축 시작 ---");
        System.out.println("출력 파일: " + outputZip.toAbsolutePath());

        // try-with-resources 구문을 사용하여 Compressor가 자동으로 닫히도록 합니다.
        try (MultiThreadedZipCompressor compressor = new MultiThreadedZipCompressor()) {
            // compressFiles 메서드 호출
            CompressionResult result = compressor.compressFiles(sourceFiles, outputZip);

            // 결과 출력
            System.out.println("기본 압축 성공!");
            System.out.println(result.getDetailedSummary());
        }
    }

    /**
     * ZIP 파일 내부에 저장될 경로를 직접 지정하는 방법을 보여줍니다.
     */
    private static void runMappedCompression(Path file1, Path file2, Path file3, Path outputZip) throws IOException {
        System.out.println("\n--- 예제 2: 경로 지정 압축 시작 ---");
        System.out.println("출력 파일: " + outputZip.toAbsolutePath());

        // 소스 파일 경로와 ZIP 내부 경로를 매핑하는 Map 생성
        Map<Path, String> fileMap = new HashMap<>();
        fileMap.put(file1, "docs/mydocument.txt"); // 경로 및 이름 변경
        fileMap.put(file2, "backup/data.bin");
        fileMap.put(file3, "reports/2025/final_report.csv"); // 깊은 경로 지정

        try (MultiThreadedZipCompressor compressor = new MultiThreadedZipCompressor()) {
            // Map을 인자로 받는 compressFiles 메서드 호출
            CompressionResult result = compressor.compressFiles(fileMap, outputZip);

            // 결과 출력
            System.out.println("경로 지정 압축 성공!");
            System.out.println(result.getDetailedSummary());
        }
    }
}
