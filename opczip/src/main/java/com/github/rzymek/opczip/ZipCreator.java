package com.github.rzymek.opczip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 단일 파일을 새로운 ZIP 아카이브로 만드는 기능을 구현한 클래스입니다.
 * ZIP 파일의 각 구성요소(Local Header, Data, Central Directory, EOCD)를
 * 별도의 메소드로 분리하여 구조를 명확하게 했습니다.
 */
public class ZipCreator {

    // ZIP 파일 구조에 사용되는 시그니처 (Little Endian 기준)
    private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
    private static final int CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50;
    private static final int END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;

    public static void main(String[] args) {
        try {
            // --- 데모용 테스트 파일 준비 ---
            Path sourceFilePath = Paths.get("sourceFile.txt");
            Path zipFilePath = Paths.get("new_archive.zip");
            String fileNameInZip = "docs/sourceFile.txt";

            // 1. 테스트를 위해 기존 파일 삭제
            Files.deleteIfExists(sourceFilePath);
            Files.deleteIfExists(zipFilePath);

            // 2. 압축할 원본 파일 생성
            Files.write(sourceFilePath, "이것은 ZIP 파일로 압축될 원본 파일의 내용입니다.".getBytes(StandardCharsets.UTF_8));
            System.out.println("압축할 원본 파일 생성: " + sourceFilePath.toAbsolutePath());

            // --- 핵심 로직 실행 ---
            System.out.println("\n단일 파일로 새 ZIP 아카이브 생성을 시작합니다...");
            createZipFromSingleFile(sourceFilePath, zipFilePath, fileNameInZip);

            System.out.println("\n작업 완료! " + zipFilePath.getFileName() + " 파일이 성공적으로 생성되었습니다.");
            System.out.println("표준 ZIP 유틸리티로 파일이 정상적으로 열리는지 확인해보세요.");

        } catch (IOException e) {
            System.err.println("오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 단일 파일을 받아 새로운 ZIP 파일을 생성하는 메인 오케스트레이션 메소드.
     */
    public static void createZipFromSingleFile(Path sourceFilePath, Path zipFilePath, String fileNameInZip) throws IOException {
        // 0. 압축에 필요한 정보 사전 준비
        byte[] sourceBytes = Files.readAllBytes(sourceFilePath);
        byte[] fileNameBytes = fileNameInZip.getBytes(StandardCharsets.UTF_8);
        byte[] compressedBytes = compress(sourceBytes);

        CRC32 crc = new CRC32();
        crc.update(sourceBytes);
        long crcValue = crc.getValue();
        long dosTime = toDosTime(LocalDateTime.now());

        try (FileChannel destChannel = FileChannel.open(zipFilePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) {
            // 1. Local File Header 작성
            writeLocalFileHeader(destChannel, fileNameBytes, compressedBytes.length, sourceBytes.length, crcValue, dosTime);

            // 2. File Data (압축된 데이터) 작성
            writeFileData(destChannel, compressedBytes);

            // 3. Central Directory 작성
            long localHeaderOffset = 0; // 이 예제에서는 파일이 하나이므로 오프셋은 항상 0
            System.out.println("Local Header Offset: " + localHeaderOffset);
            long centralDirectoryOffset = destChannel.position(); // 현재 위치가 CD의 시작점
            System.out.println("Central Directory Offset: " + centralDirectoryOffset);

            writeCentralDirectory(destChannel, fileNameBytes, compressedBytes.length, sourceBytes.length, crcValue, dosTime, localHeaderOffset);
            long centralDirectorySize = destChannel.position() - centralDirectoryOffset;

            // 4. End of Central Directory (EOCD) 작성
            writeEndOfCentralDirectory(destChannel, 1, centralDirectoryOffset, centralDirectorySize);
        }
    }

    /**
     * 1) Local File Header 부분을 파일에 씁니다.
     */
    private static void writeLocalFileHeader(FileChannel channel, byte[] fileNameBytes, int compressedSize, int uncompressedSize, long crc32, long dosTime) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(30 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(LOCAL_FILE_HEADER_SIGNATURE);
        buffer.putShort((short) 20); // Version needed to extract
        buffer.putShort((short) 0);  // General purpose bit flag
        buffer.putShort((short) 8);  // Compression method (DEFLATE)
        buffer.putInt((int) dosTime); // Last mod file time & date
        buffer.putInt((int) crc32);
        buffer.putInt(compressedSize);
        buffer.putInt(uncompressedSize);
        buffer.putShort((short) fileNameBytes.length);
        buffer.putShort((short) 0); // Extra field length
        buffer.put(fileNameBytes);

        buffer.flip();
        channel.write(buffer);
    }

    /**
     * 2) 압축된 파일 데이터를 파일에 씁니다.
     */
    private static void writeFileData(FileChannel channel, byte[] compressedData) throws IOException {
        channel.write(ByteBuffer.wrap(compressedData));
    }

    /**
     * 3) Central Directory File Header 부분을 파일에 씁니다.
     */
    private static void writeCentralDirectory(FileChannel channel, byte[] fileNameBytes, int compressedSize, int uncompressedSize, long crc32, long dosTime, long localHeaderOffset) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(46 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE);
        buffer.putShort((short) 20); // Version made by
        buffer.putShort((short) 20); // Version needed to extract
        buffer.putShort((short) 0);  // General purpose bit flag
        buffer.putShort((short) 8);  // Compression method
        buffer.putInt((int) dosTime); // Last mod file time & date
        buffer.putInt((int) crc32);
        buffer.putInt(compressedSize);
        buffer.putInt(uncompressedSize);
        buffer.putShort((short) fileNameBytes.length);
        buffer.putShort((short) 0); // Extra field length
        buffer.putShort((short) 0); // File comment length
        buffer.putShort((short) 0); // Disk number start
        buffer.putShort((short) 0); // Internal file attributes
        buffer.putInt(0);           // External file attributes
        buffer.putInt((int) localHeaderOffset); // Relative offset of local header
        buffer.put(fileNameBytes);

        buffer.flip();
        channel.write(buffer);
    }

    /**
     * 4) End Of Central Directory 부분을 파일에 씁니다.
     */
    private static void writeEndOfCentralDirectory(FileChannel channel, int totalEntries, long cdOffset, long cdSize) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(22);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(END_OF_CENTRAL_DIRECTORY_SIGNATURE);
        buffer.putShort((short) 0); // Number of this disk
        buffer.putShort((short) 0); // Disk where central directory starts
        buffer.putShort((short) totalEntries); // Number of entries on this disk
        buffer.putShort((short) totalEntries); // Total number of entries
        buffer.putInt((int) cdSize); // Size of central directory
        buffer.putInt((int) cdOffset); // Offset of start of central directory
        buffer.putShort((short) 0); // .ZIP file comment length

        buffer.flip();
        channel.write(buffer);
    }

    /**
     * 데이터를 Deflate 알고리즘으로 압축합니다.
     */
    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // `nowrap=true`는 ZIP 파일 형식에 필요한 순수 DEFLATE 스트림을 생성합니다.
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try (java.util.zip.DeflaterOutputStream dos = new java.util.zip.DeflaterOutputStream(baos, deflater)) {
            dos.write(data);
        }
        deflater.end();
        return baos.toByteArray();
    }

    /**
     * Java LocalDateTime을 MS-DOS 시간/날짜 형식으로 변환합니다.
     */
    private static long toDosTime(LocalDateTime time) {
        int year = time.getYear();
        if (year < 1980) year = 1980;

        return ((long) (year - 1980) << 25) |
                ((long) time.getMonthValue() << 21) |
                ((long) time.getDayOfMonth() << 16) |
                ((long) time.getHour() << 11) |
                ((long) time.getMinute() << 5) |
                ((long) time.getSecond() >> 1);
    }
}
