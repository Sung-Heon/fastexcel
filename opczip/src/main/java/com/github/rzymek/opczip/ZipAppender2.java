package com.github.rzymek.opczip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 기존 ZIP 파일에 파일을 추가하는 기능을 구현한 클래스입니다.
 * java.util.zip의 고수준 API를 사용하지 않고, ZIP 파일 형식을 직접 조작합니다.
 * 참고: 이 코드는 ZIP64 확장을 지원하지 않습니다. (파일 크기 ~4GB, 엔트리 수 ~65535개 제한)
 */
public class ZipAppender2 {

    // ZIP 파일 구조에 사용되는 시그니처 (Little Endian 기준)
    private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
    private static final int CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50;
    private static final int END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;

    /**
     * EOCD(End of Central Directory) 레코드 정보를 담는 클래스.
     * Java 16+의 record를 사용하면 더 간결하게 표현할 수 있습니다.
     */
    private static class EOCDRecord {
        long centralDirectoryOffset;
        int totalEntries;
        long centralDirectorySize;
    }

    public static void main(String[] args) {
        try {
            // --- 데모용 테스트 파일 준비 ---
            Path originalZipPath = Paths.get("example.zip");
            Path fileToAddPath = Paths.get("newFile.txt");

            // 1. 기존 ZIP 파일 생성 (테스트를 위해 비어있는 ZIP 파일 생성)
            // 실제로는 이 파일이 이미 존재한다고 가정합니다.
            createDummyZip(originalZipPath);
            System.out.println("테스트용 원본 ZIP 파일 생성: " + originalZipPath.toAbsolutePath());

            // 2. 추가할 파일 생성
            Files.write(fileToAddPath, "이것은 새로 추가될 파일의 내용입니다.".getBytes(StandardCharsets.UTF_8));
            System.out.println("추가할 테스트 파일 생성: " + fileToAddPath.toAbsolutePath());

            // --- 핵심 로직 실행 ---
            System.out.println("\nZIP 파일에 새 파일 추가 작업을 시작합니다...");
            addFileToZip(originalZipPath, fileToAddPath, "added/newFile.txt");

            System.out.println("\n작업 완료! " + originalZipPath.getFileName() + " 파일이 성공적으로 업데이트되었습니다.");
            System.out.println("표준 ZIP 유틸리티로 파일이 정상적으로 열리는지 확인해보세요.");

        } catch (IOException e) {
            System.err.println("오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 기존 ZIP 파일에 새로운 파일을 추가합니다.
     *
     * @param originalZipPath 원본 ZIP 파일 경로
     * @param newFilePath 추가할 파일 경로
     * @param fileNameInZip ZIP 파일 내에 저장될 파일 이름 (경로 포함 가능)
     * @throws IOException I/O 오류 발생 시
     */
    public static void addFileToZip(Path originalZipPath, Path newFilePath, String fileNameInZip) throws IOException {
        // 부모 경로가 없는 경우 (상대 경로인 경우) 임시 디렉토리를 사용합니다.
        Path tempDir = originalZipPath.getParent();
        if (tempDir == null) {
            tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        }
        Path tempZipPath = Files.createTempFile(tempDir, "zip_temp", ".tmp");

        try (RandomAccessFile raf = new RandomAccessFile(originalZipPath.toFile(), "r");
             FileChannel sourceChannel = raf.getChannel();
             FileChannel destChannel = FileChannel.open(tempZipPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {

            // 1. EOCD 레코드를 찾아 파싱합니다.
            EOCDRecord eocd = findAndParseEOCD(raf);
            if (eocd == null) {
                throw new IOException("유효한 ZIP 파일이 아니거나 EOCD 레코드를 찾을 수 없습니다.");
            }

            // 2. 기존 ZIP 파일의 데이터 부분 (Central Directory 이전까지)을 임시 파일에 복사합니다.
            sourceChannel.transferTo(0, eocd.centralDirectoryOffset, destChannel);
            long newEntryOffset = destChannel.position();

            // 3. 추가할 파일을 압축하고, Local File Header와 함께 임시 파일에 씁니다.
            byte[] fileBytes = Files.readAllBytes(newFilePath);
            byte[] fileNameBytes = fileNameInZip.getBytes(StandardCharsets.UTF_8);

            // CRC-32 계산
            CRC32 crc = new CRC32();
            crc.update(fileBytes);
            long crcValue = crc.getValue();

            // Deflate 압축
            byte[] compressedData = compress(fileBytes);

            // MS-DOS 시간/날짜 형식으로 변환
            long dosTime = toDosTime(new Date());

            // Local File Header 작성
            ByteBuffer localHeader = createLocalFileHeader(fileNameBytes, compressedData.length, fileBytes.length, crcValue, dosTime);
            destChannel.write(localHeader);
            destChannel.write(ByteBuffer.wrap(compressedData));

            // 4. 새로운 Central Directory의 시작 위치를 기록합니다.
            long newCentralDirectoryOffset = destChannel.position();

            // 5. 기존 Central Directory를 임시 파일에 복사합니다.
            sourceChannel.position(eocd.centralDirectoryOffset);
            destChannel.transferFrom(sourceChannel, newCentralDirectoryOffset, eocd.centralDirectorySize);

            // 6. 새로 추가된 파일에 대한 Central Directory File Header를 임시 파일에 추가합니다.
            ByteBuffer cdHeader = createCentralDirectoryHeader(fileNameBytes, compressedData.length, fileBytes.length, crcValue, dosTime, newEntryOffset);
            destChannel.write(cdHeader);

            // 7. 업데이트된 정보로 새로운 EOCD 레코드를 작성합니다.
            ByteBuffer newEocd = createNewEOCD(
                    eocd.totalEntries + 1,
                    newCentralDirectoryOffset,
                    eocd.centralDirectorySize + cdHeader.capacity()
            );
            destChannel.write(newEocd);

        } catch (IOException e) {
            // 오류 발생 시 임시 파일 삭제
            Files.deleteIfExists(tempZipPath);
            throw e;
        }

        // 8. 모든 작업이 성공하면, 원본 파일을 임시 파일로 원자적으로 교체합니다.
        Files.move(tempZipPath, originalZipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * 파일 끝에서부터 EOCD 시그니처를 찾아 레코드를 파싱합니다.
     */
    private static EOCDRecord findAndParseEOCD(RandomAccessFile raf) throws IOException {
        long fileSize = raf.length();
        // EOCD는 파일 끝에 위치하며, 주석 길이에 따라 위치가 가변적입니다.
        // 최대 주석 길이는 65535 바이트입니다.
        long scanStartPos = fileSize - 22; // 최소 EOCD 크기
        if (scanStartPos < 0) return null;

        long maxScanSize = Math.min(fileSize, 65535 + 22);
        long searchBoundary = fileSize - maxScanSize;
        if (searchBoundary < 0) searchBoundary = 0;

        // 파일 끝에서부터 시그니처를 스캔합니다.
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

                EOCDRecord record = new EOCDRecord();
                record.totalEntries = Short.toUnsignedInt(buffer.getShort()); // 전체 엔트리 수
                record.centralDirectorySize = Integer.toUnsignedLong(buffer.getInt()); // CD 크기
                record.centralDirectoryOffset = Integer.toUnsignedLong(buffer.getInt()); // CD 시작 오프셋

                return record;
            }
        }
        return null;
    }

    /**
     * Local File Header를 생성합니다.
     */
    private static ByteBuffer createLocalFileHeader(byte[] fileNameBytes, int compressedSize, int uncompressedSize, long crc32, long dosTime) {
        ByteBuffer buffer = ByteBuffer.allocate(30 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(LOCAL_FILE_HEADER_SIGNATURE); // 4 bytes
        buffer.putShort((short) 20); // Version needed to extract (2.0)
        buffer.putShort((short) 0); // General purpose bit flag
        buffer.putShort((short) 8); // Compression method (DEFLATE)
        buffer.putInt((int) dosTime); // Last mod file time & date
        buffer.putInt((int) crc32); // CRC-32
        buffer.putInt(compressedSize); // Compressed size
        buffer.putInt(uncompressedSize); // Uncompressed size
        buffer.putShort((short) fileNameBytes.length); // File name length
        buffer.putShort((short) 0); // Extra field length
        buffer.put(fileNameBytes); // File name

        buffer.flip();
        return buffer;
    }

    /**
     * Central Directory File Header를 생성합니다.
     */
    private static ByteBuffer createCentralDirectoryHeader(byte[] fileNameBytes, int compressedSize, int uncompressedSize, long crc32, long dosTime, long localHeaderOffset) {
        ByteBuffer buffer = ByteBuffer.allocate(46 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE); // 4 bytes
        buffer.putShort((short) 20); // Version made by
        buffer.putShort((short) 20); // Version needed to extract
        buffer.putShort((short) 0); // General purpose bit flag
        buffer.putShort((short) 8); // Compression method
        buffer.putInt((int) dosTime); // Last mod file time & date
        buffer.putInt((int) crc32); // CRC-32
        buffer.putInt(compressedSize); // Compressed size
        buffer.putInt(uncompressedSize); // Uncompressed size
        buffer.putShort((short) fileNameBytes.length); // File name length
        buffer.putShort((short) 0); // Extra field length
        buffer.putShort((short) 0); // File comment length
        buffer.putShort((short) 0); // Disk number start
        buffer.putShort((short) 0); // Internal file attributes
        buffer.putInt(0); // External file attributes
        buffer.putInt((int) localHeaderOffset); // Relative offset of local header
        buffer.put(fileNameBytes); // File name

        buffer.flip();
        return buffer;
    }

    /**
     * 새로운 EOCD 레코드를 생성합니다.
     */
    private static ByteBuffer createNewEOCD(int totalEntries, long cdOffset, long cdSize) {
        ByteBuffer buffer = ByteBuffer.allocate(22);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(END_OF_CENTRAL_DIRECTORY_SIGNATURE); // 4 bytes
        buffer.putShort((short) 0); // Number of this disk
        buffer.putShort((short) 0); // Disk where central directory starts
        buffer.putShort((short) totalEntries); // Number of entries on this disk
        buffer.putShort((short) totalEntries); // Total number of entries
        buffer.putInt((int) cdSize); // Size of central directory
        buffer.putInt((int) cdOffset); // Offset of start of central directory
        buffer.putShort((short) 0); // .ZIP file comment length

        buffer.flip();
        return buffer;
    }

    /**
     * 데이터를 Deflate 알고리즘으로 압축합니다.
     */
    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true); // `nowrap=true` for raw deflate stream
        try (java.util.zip.DeflaterOutputStream dos = new java.util.zip.DeflaterOutputStream(baos, deflater)) {
            dos.write(data);
        }
        deflater.end();
        return baos.toByteArray();
    }

    /**
     * Java Date를 MS-DOS 시간/날짜 형식으로 변환합니다.
     */
    @SuppressWarnings("deprecation")
    private static long toDosTime(Date date) {
        int year = date.getYear() + 1900;
        if (year < 1980) {
            return (1 << 21) | (1 << 16);
        }
        int month = date.getMonth() + 1;
        int day = date.getDate();
        int hour = date.getHours();
        int minute = date.getMinutes();
        int second = date.getSeconds();
        return ((year - 1980) & 0x7f) << 25 |
                (month & 0xf) << 21 |
                (day & 0x1f) << 16 |
                (hour & 0x1f) << 11 |
                (minute & 0x3f) << 5 |
                (second >> 1 & 0x1f);
    }

    /**
     * 테스트용으로 비어있는 ZIP 파일을 생성합니다. (PK\x05\x06\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00)
     */
    private static void createDummyZip(Path path) throws IOException {
        if (Files.exists(path)) return;
        ByteBuffer emptyZip = createNewEOCD(0, 0, 0);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.write(emptyZip);
        }
    }
}
