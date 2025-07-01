package com.github.rzymek.opczip;

import java.io.*;
import java.util.zip.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ZipCreatorExample {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws IOException {
        try {
            // 1. ZIP 파일 생성
            System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - Creating ZIP file...");
            try (FileOutputStream fos = new FileOutputStream("example.zip");
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                // 2. ZIP 파일 주석 설정
                String zipComment = "Example ZIP file created at " + new java.util.Date();
                zos.setComment(zipComment);
                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - ZIP file comment set: " + zipComment);

                // 3. 압축 방식 설정
                zos.setMethod(ZipEntry.DEFLATED);
                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - Compression method set to DEFLATED");

                // 4. 압축 레벨 설정
                zos.setLevel(Deflater.DEFAULT_COMPRESSION);
                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - Compression level set to DEFAULT_COMPRESSION");

                // 5. 엔트리 추가 예시
                // (1) 텍스트 파일 추가
                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - Adding text file entry...");
                String textContent = "Hello, ZIP!";
                ZipEntry textEntry = new ZipEntry("hel123lo.txt");
                textEntry.setComment("Sample text file");
                zos.putNextEntry(textEntry);
                zos.write(textContent.getBytes());
                zos.closeEntry();
                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - Text file entry added successfully");

                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - Adding text file entry...");
                String textContent2 = "Hel1lo, ZIP223123!";
                ZipEntry textEntry2 = new ZipEntry("hello123213.txt");
                textEntry2.setComment("Sample text file");
                zos.putNextEntry(textEntry2);
                zos.write(textContent2.getBytes());
                zos.closeEntry();

                // 6. ZIP 파일 마무리
                zos.finish();
                System.out.println("[INFO] " + LocalDateTime.now().format(formatter) + " - ZIP file creation completed successfully");
            }
        } catch (IOException e) {
            System.err.println("[ERROR] " + LocalDateTime.now().format(formatter) + " - Error creating ZIP file: " + e.getMessage());
            throw e;
        }
    }
}