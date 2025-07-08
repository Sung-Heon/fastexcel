package com.github.rzymek.opczip;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ZipAppender를 사용하여 기존 ZIP 파일에 항목을 추가하는 예제
 */
public class ZipAppenderExample {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        Path zipPath = Paths.get("example.zip");

        try {
            System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - ZipAppender 예제를 시작합니다.");

            // ZipAppender 인스턴스 생성 (기존 파일이 없으면 새로 생성)
            try (ZipAppender appender = new ZipAppender(zipPath)) {
                // 1. 텍스트 파일 항목 추가
                String textContent = "이것은 ZipAppender로 추가된 텍스트 파일입니다.\n" +
                                     "추가 시간: " + LocalDateTime.now().format(formatter);

                appender.addTextEntry("readme.txt", textContent);
                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - 텍스트 파일 'readme.txt' 추가 완료");

                // 2. 또 다른 텍스트 파일 항목 추가 (압축 없음)
                String noCompressContent = "이 항목은 압축되지 않고 저장됩니다.";
                appender.addEntry("no-compression.txt", noCompressContent.getBytes(), 0); // 0: 압축 없음
                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - 압축되지 않은 파일 'no-compression.txt' 추가 완료");

                // 3. 최대 압축 레벨로 텍스트 파일 항목 추가
                StringBuilder largeContent = new StringBuilder();
                for (int i = 0; i < 1000; i++) {
                    largeContent.append("반복되는 내용입니다. 이 내용은 높은 압축률을 얻을 수 있습니다. #").append(i).append("\n");
                }

                appender.addEntry("max-compression.txt", largeContent.toString().getBytes(), 9); // 9: 최대 압축
                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - 최대 압축 파일 'max-compression.txt' 추가 완료");

                // 4. 이진 데이터 항목 추가
                byte[] binaryData = new byte[1024];
                // 간단한 패턴으로 이진 데이터 생성
                for (int i = 0; i < binaryData.length; i++) {
                    binaryData[i] = (byte) (i & 0xFF);
                }

                appender.addEntry("binary-data.bin", binaryData);
                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - 이진 파일 'binary-data.bin' 추가 완료");
            }

            System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - ZIP 파일 업데이트 완료: " + zipPath);
            System.out.println("이제 아래 명령으로 ZIP 파일 내용을 확인할 수 있습니다:");
            System.out.println("  unzip -l " + zipPath);

        } catch (IOException e) {
            System.err.println("[ERROR] " + LocalDateTime.now().format(formatter) + " - 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
