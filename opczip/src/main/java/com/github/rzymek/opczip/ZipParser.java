package com.github.rzymek.opczip;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 기존 ZIP 파일의 구조를 분석하여 EOCD와 Central Directory의 내용을
 * 해석하고 출력하는 클래스입니다.
 */
public class ZipParser {

    // ZIP 파일 구조에 사용되는 시그니처 (Little Endian 기준)
    private static final int CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50;
    private static final int END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;

    /**
     * EOCD(End of Central Directory) 레코드 정보를 담는 데이터 클래스.
     * Java 16+의 record를 사용하면 더 간결하게 표현할 수 있습니다.
     */
    private static class EOCDRecord {
        final int totalEntries;
        final long centralDirectorySize;
        final long centralDirectoryOffset;
        final String comment;

        EOCDRecord(int totalEntries, long centralDirectorySize, long centralDirectoryOffset, String comment) {
            this.totalEntries = totalEntries;
            this.centralDirectorySize = centralDirectorySize;
            this.centralDirectoryOffset = centralDirectoryOffset;
            this.comment = comment;
        }
    }

    /**
     * Central Directory의 개별 파일 헤더 정보를 담는 데이터 클래스.
     */
    private static class CentralDirectoryFileHeader {
        final String fileName;
        final long compressedSize;
        final long uncompressedSize;
        final long crc32;
        final LocalDateTime lastModified;
        final long localHeaderOffset;

        CentralDirectoryFileHeader(String fileName, long compressedSize, long uncompressedSize, long crc32, LocalDateTime lastModified, long localHeaderOffset) {
            this.fileName = fileName;
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
            this.crc32 = crc32;
            this.lastModified = lastModified;
            this.localHeaderOffset = localHeaderOffset;
        }
    }

    public static void main(String[] args) {
        try {
            // --- 분석할 ZIP 파일 준비 ---
            // 이전 단계에서 만든 ZipCreator를 사용하여 분석용 파일을 생성합니다.
            Path zipFilePath = Paths.get("new_archive.zip");
            if (!Files.exists(zipFilePath)) {
                System.out.println("분석할 파일이 없어 ZipCreator를 이용해 테스트 파일을 생성합니다...");
                ZipCreator.createZipFromSingleFile(Paths.get("sourceFile.txt"), zipFilePath, "docs/sourceFile.txt");
            }

            System.out.println("ZIP 파일 분석을 시작합니다: " + zipFilePath.toAbsolutePath());
            System.out.println("==================================================");

            parseAndPrintInfo(zipFilePath);

        } catch (IOException e) {
            System.err.println("오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ZIP 파일을 분석하고 그 정보를 콘솔에 출력합니다.
     */
    public static void parseAndPrintInfo(Path zipFilePath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(zipFilePath.toFile(), "r")) {
            // 1. EOCD를 찾아 파싱합니다.
            EOCDRecord eocd = findAndParseEOCD(raf);
            if (eocd == null) {
                System.err.println("오류: EOCD(End of Central Directory) 레코드를 찾을 수 없습니다.");
                return;
            }

            // 2. 파싱된 EOCD 정보를 출력합니다.
            System.out.println("[EOCD 정보]");
            System.out.printf("  - Central Directory 시작 오프셋: %d (0x%X)\n", eocd.centralDirectoryOffset, eocd.centralDirectoryOffset);
            System.out.printf("  - Central Directory 크기: %d bytes\n", eocd.centralDirectorySize);
            System.out.printf("  - 총 엔트리(파일) 개수: %d\n", eocd.totalEntries);
            System.out.printf("  - ZIP 파일 주석: %s\n", eocd.comment.isEmpty() ? "(없음)" : eocd.comment);
            System.out.println("--------------------------------------------------");

            // 3. Central Directory를 파싱합니다.
            List<CentralDirectoryFileHeader> headers = parseCentralDirectory(raf, eocd);

            // 4. 파싱된 Central Directory 헤더 정보를 출력합니다.
            System.out.println("[Central Directory 엔트리 목록]");
            for (int i = 0; i < headers.size(); i++) {
                CentralDirectoryFileHeader header = headers.get(i);
                System.out.printf("  엔트리 #%d:\n", i + 1);
                System.out.printf("    - 파일 이름: %s\n", header.fileName);
                System.out.printf("    - 수정 시각: %s\n", header.lastModified);
                System.out.printf("    - CRC-32: 0x%X\n", header.crc32);
                System.out.printf("    - 압축된 크기: %d bytes\n", header.compressedSize);
                System.out.printf("    - 원본 크기: %d bytes\n", header.uncompressedSize);
                System.out.printf("    - Local Header 오프셋: %d (0x%X)\n", header.localHeaderOffset, header.localHeaderOffset);
            }
            System.out.println("==================================================");
        }
    }

    /**
     * 파일 끝에서부터 EOCD 시그니처를 찾아 레코드를 파싱합니다.
     */
    private static EOCDRecord findAndParseEOCD(RandomAccessFile raf) throws IOException {
        long fileSize = raf.length();
        // EOCD는 파일 끝에 위치하며, 주석 길이에 따라 위치가 가변적입니다.
        long scanStartPos = fileSize - 22; // 최소 EOCD 크기
        if (scanStartPos < 0) return null;

        long maxScanSize = Math.min(fileSize, 65535 + 22);
        long searchBoundary = fileSize - maxScanSize;
        if (searchBoundary < 0) searchBoundary = 0;

        for (long pos = scanStartPos; pos >= searchBoundary; pos--) {
            raf.seek(pos);
            if (raf.readInt() == Integer.reverseBytes(END_OF_CENTRAL_DIRECTORY_SIGNATURE)) {
                raf.seek(pos);
                ByteBuffer buffer = ByteBuffer.allocate(22);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                raf.getChannel().read(buffer);
                buffer.flip();

                buffer.getInt(); // 시그니처 건너뛰기
                buffer.getShort(); // 이 디스크의 번호
                buffer.getShort(); // CD가 시작되는 디스크 번호
                buffer.getShort(); // 이 디스크의 엔트리 수

                int totalEntries = Short.toUnsignedInt(buffer.getShort());
                long cdSize = Integer.toUnsignedLong(buffer.getInt());
                long cdOffset = Integer.toUnsignedLong(buffer.getInt());
                int commentLength = Short.toUnsignedInt(buffer.getShort());

                String comment = "";
                if (commentLength > 0) {
                    raf.seek(pos + 22);
                    byte[] commentBytes = new byte[commentLength];
                    raf.readFully(commentBytes);
                    comment = new String(commentBytes, StandardCharsets.UTF_8);
                }

                return new EOCDRecord(totalEntries, cdSize, cdOffset, comment);
            }
        }
        return null; // EOCD를 찾지 못함
    }

    /**
     * Central Directory의 모든 파일 헤더를 파싱하여 리스트로 반환합니다.
     */
    private static List<CentralDirectoryFileHeader> parseCentralDirectory(RandomAccessFile raf, EOCDRecord eocd) throws IOException {
        List<CentralDirectoryFileHeader> headers = new ArrayList<>();
        raf.seek(eocd.centralDirectoryOffset);

        for (int i = 0; i < eocd.totalEntries; i++) {
            // 시그니처 확인
            int signature = raf.readInt();
            if (Integer.reverseBytes(signature) != CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE) {
                throw new IOException("잘못된 Central Directory 헤더 시그니처입니다: " + Integer.toHexString(signature));
            }

            // 헤더의 고정 길이 부분 읽기
            byte[] fixedHeaderPart = new byte[42];
            raf.readFully(fixedHeaderPart);
            ByteBuffer buffer = ByteBuffer.wrap(fixedHeaderPart);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // 필드 파싱
            buffer.getShort(); // Version made by
            buffer.getShort(); // Version needed to extract
            buffer.getShort(); // General purpose bit flag
            buffer.getShort(); // Compression method
            long dosTime = Integer.toUnsignedLong(buffer.getInt());
            long crc32 = Integer.toUnsignedLong(buffer.getInt());
            long compressedSize = Integer.toUnsignedLong(buffer.getInt());
            long uncompressedSize = Integer.toUnsignedLong(buffer.getInt());
            int fileNameLength = Short.toUnsignedInt(buffer.getShort());
            int extraFieldLength = Short.toUnsignedInt(buffer.getShort());
            int fileCommentLength = Short.toUnsignedInt(buffer.getShort());
            buffer.getShort(); // Disk number start
            buffer.getShort(); // Internal file attributes
            buffer.getInt();   // External file attributes
            long localHeaderOffset = Integer.toUnsignedLong(buffer.getInt());

            // 가변 길이 필드(파일 이름 등) 읽기
            byte[] fileNameBytes = new byte[fileNameLength];
            raf.readFully(fileNameBytes);
            String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);

            // Extra 필드와 주석 필드는 건너뜀
            raf.skipBytes(extraFieldLength);
            raf.skipBytes(fileCommentLength);

            headers.add(new CentralDirectoryFileHeader(
                    fileName, compressedSize, uncompressedSize, crc32, fromDosTime(dosTime), localHeaderOffset
            ));
        }
        return headers;
    }

    /**
     * MS-DOS 시간/날짜 형식을 Java LocalDateTime으로 변환합니다.
     */
    private static LocalDateTime fromDosTime(long dosTime) {
        int year = (int) (((dosTime >> 25) & 0x7f) + 1980);
        int month = (int) ((dosTime >> 21) & 0x0f);
        int day = (int) ((dosTime >> 16) & 0x1f);
        int hour = (int) ((dosTime >> 11) & 0x1f);
        int minute = (int) ((dosTime >> 5) & 0x3f);
        int second = (int) ((dosTime & 0x1f) * 2);
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }
}

